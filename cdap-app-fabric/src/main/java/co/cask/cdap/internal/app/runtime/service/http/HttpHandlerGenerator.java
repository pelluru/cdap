/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.internal.app.runtime.service.http;

import co.cask.cdap.api.annotation.TransactionControl;
import co.cask.cdap.api.annotation.TransactionPolicy;
import co.cask.cdap.api.metrics.MetricsContext;
import co.cask.cdap.api.service.http.HttpContentConsumer;
import co.cask.cdap.api.service.http.HttpServiceHandler;
import co.cask.cdap.api.service.http.HttpServiceRequest;
import co.cask.cdap.api.service.http.HttpServiceResponder;
import co.cask.cdap.common.lang.ClassLoaders;
import co.cask.cdap.internal.asm.ClassDefinition;
import co.cask.cdap.internal.asm.Methods;
import co.cask.cdap.internal.asm.Signatures;
import co.cask.http.BodyConsumer;
import co.cask.http.HttpHandler;
import co.cask.http.HttpResponder;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import org.apache.tephra.TransactionContext;
import org.apache.tephra.TransactionFailureException;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

/**
 * A bytecode generator for generating class that implements {@link co.cask.http.HttpHandler} interface and
 * copy public methods annotated with {@link Path} of a delegating class by delegating to the delegation instance.
 *
 * It is needed for wrapping user class that annotated with {@link Path} into a class that implements
 * {@link co.cask.http.HttpHandler} for the netty http service to inspect.
 *
 * Also, the generated class can impose transaction boundary for calls to those {@link Path @Path} methods.
 *
 * The generated class has a skeleton looks like this:
 *
 * <pre>{@code
 *   public final class GeneratedClassName extends AbstractHttpHandlerDelegator<UserHandlerClass> {
 *
 *     public GeneratedClassName(DelegatorContext<UserHandlerClass> instantiator) {
 *       super(context);
 *     }
 *
 *     @literal @GET
 *     @literal @Path("/path")
 *     public void userMethod(HttpRequest request, HttpResponder responder) {
 *       // see generateTransactionalDelegateBody() for generated method body.
 *     }
 *
 *     @literal @PUT
 *     @literal @Path("/upload")
 *     public HttpContentConsumer userUpload(HttpRequest request, HttpResponder responder) {
 *       // see generateTransactionalDelegateBody() for generated method body.
 *     }
 *   }
 * }</pre>
 */
final class HttpHandlerGenerator {

  private static final Set<Type> HTTP_ANNOTATION_TYPES = ImmutableSet.of(
    Type.getType(GET.class),
    Type.getType(POST.class),
    Type.getType(PUT.class),
    Type.getType(DELETE.class),
    Type.getType(HEAD.class)
  );

  private static final Type TX_POLICY_TYPE = Type.getType(TransactionPolicy.class);
  private static final Type TX_CONTROL_TYPE = Type.getType(TransactionControl.class);

  /**
   * Generates a new class that implements {@link HttpHandler} by copying methods signatures from the given
   * {@link HttpServiceHandler} class. Calls to {@link HttpServiceHandler} methods are transactional unless
   * the method is annotated with {@link TransactionPolicy(TransactionControl)}.
   *
   * @param delegateType type of the {@link HttpServiceHandler}
   * @param pathPrefix prefix for all {@code @PATH} annotation
   * @return A {@link ClassDefinition} containing information of the newly generated class.
   * @throws IOException if failed to generate the class.
   */
  ClassDefinition generate(TypeToken<? extends HttpServiceHandler> delegateType, String pathPrefix) throws IOException {
    Class<?> rawType = delegateType.getRawType();
    List<Class<?>> preservedClasses = Lists.newArrayList();
    preservedClasses.add(rawType);

    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

    String internalName = Type.getInternalName(rawType);
    String className = internalName + Hashing.md5().hashString(internalName);

    // Generate the class
    Type classType = Type.getObjectType(className);
    classWriter.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
                      className, getClassSignature(delegateType),
                      Type.getInternalName(AbstractHttpHandlerDelegator.class), null);

    // Inspect the delegate class hierarchy to generate public handler methods.
    for (TypeToken<?> type : delegateType.getTypes().classes()) {
      if (!Object.class.equals(type.getRawType())) {
        inspectHandler(delegateType, type, pathPrefix, classType, classWriter, preservedClasses);
      }
    }

    generateConstructor(delegateType, classWriter);
    generateLogger(classType, classWriter);

    ClassDefinition classDefinition = new ClassDefinition(classWriter.toByteArray(), className, preservedClasses);
    // DEBUG block. Uncomment for debug
    // co.cask.cdap.internal.asm.Debugs.debugByteCode(classDefinition, new java.io.PrintWriter(System.out));
    // End DEBUG block
    return classDefinition;
  }

  /**
   * Inspects the given type and copy/rewrite handler methods from it into the newly generated class.
   *
   * @param delegateType The user handler type
   * @param inspectType The type that needs to be inspected. It's either the delegateType or one of its parents
   */
  private void inspectHandler(final TypeToken<?> delegateType, final TypeToken<?> inspectType, final String pathPrefix,
                              final Type classType, final ClassWriter classWriter,
                              final List<Class<?>> preservedClasses) throws IOException {
    Class<?> rawType = inspectType.getRawType();

    // Visit the delegate class, copy and rewrite handler method, with method body just do delegation
    try (
      InputStream sourceBytes = rawType.getClassLoader().getResourceAsStream(Type.getInternalName(rawType) + ".class")
    ) {
      ClassReader classReader = new ClassReader(sourceBytes);
      classReader.accept(new ClassVisitor(Opcodes.ASM5) {

        // Only need to visit @Path at the class level if we are inspecting the user handler class
        private final boolean inspectDelegate = delegateType.equals(inspectType);
        private boolean visitedPath = !inspectDelegate;

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
          super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          // Copy the class annotation if it is @Path. Only do it for one time
          Type type = Type.getType(desc);
          if (inspectDelegate && type.equals(Type.getType(Path.class))) {
            visitedPath = true;
            AnnotationVisitor annotationVisitor = classWriter.visitAnnotation(desc, visible);
            return new AnnotationVisitor(Opcodes.ASM5, annotationVisitor) {
              @Override
              public void visit(String name, Object value) {
                // "value" is the key for the Path annotation string.
                if (name.equals("value")) {
                  super.visit(name, pathPrefix + value.toString());
                } else {
                  super.visit(name, value);
                }
              }
            };

          } else {
            return super.visitAnnotation(desc, visible);
          }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
          // Create a class-level annotation with the prefix, if the user has not specified any class-level
          // annotation.
          if (!visitedPath) {
            String pathDesc = Type.getType(Path.class).getDescriptor();
            AnnotationVisitor annotationVisitor = classWriter.visitAnnotation(pathDesc, true);
            annotationVisitor.visit("value", pathPrefix);
            annotationVisitor.visitEnd();
            visitedPath = true;
          }

          // Copy the method if it is public and annotated with one of the HTTP request method
          MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
          if (!Modifier.isPublic(access)) {
            return mv;
          }
          return new HandlerMethodVisitor(delegateType, mv, desc, signature, access, name,
                                          exceptions, classType, classWriter, preservedClasses);
        }
      }, ClassReader.SKIP_DEBUG);
    }
  }

  /**
   * Generates the constructor. The constructor generated has signature {@code (DelegatorContext, MetricsContext)}.
   */
  private void generateConstructor(TypeToken<? extends HttpServiceHandler> delegateType, ClassWriter classWriter) {
    Method constructor = Methods.getMethod(void.class, "<init>", DelegatorContext.class, MetricsContext.class);
    String signature = Signatures.getMethodSignature(constructor, TypeToken.of(void.class),
                                                     getContextType(delegateType),
                                                     TypeToken.of(MetricsContext.class));

    // Constructor(DelegatorContext, MetricsContext)
    GeneratorAdapter mg = new GeneratorAdapter(Opcodes.ACC_PUBLIC, constructor, signature, null, classWriter);

    // super(context, metricsContext);
    mg.loadThis();
    mg.loadArg(0);
    mg.loadArg(1);
    mg.invokeConstructor(Type.getType(AbstractHttpHandlerDelegator.class),
                         Methods.getMethod(void.class, "<init>", DelegatorContext.class, MetricsContext.class));
    mg.returnValue();
    mg.endMethod();
  }

  /**
   * Generates a static Logger field for logging and a static initialization block to initialize the logger.
   */
  private void generateLogger(Type classType, ClassWriter classWriter) {
    // private static final Logger LOG = LoggerFactory.getLogger(classType);
    classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "LOG",
                           Type.getType(Logger.class).getDescriptor(), null, null);
    Method init = Methods.getMethod(void.class, "<clinit>");
    GeneratorAdapter mg = new GeneratorAdapter(Opcodes.ACC_STATIC, init, null, null, classWriter);
    mg.visitLdcInsn(classType);
    mg.invokeStatic(Type.getType(LoggerFactory.class), Methods.getMethod(Logger.class, "getLogger", Class.class));
    mg.putStatic(classType, "LOG", Type.getType(Logger.class));
    mg.returnValue();
    mg.endMethod();
  }

  /**
   * Returns true if the annotation is of type {@link javax.ws.rs.HttpMethod} annotations.
   */
  private boolean isHandlerMethod(Type annotationType) {
    return HTTP_ANNOTATION_TYPES.contains(annotationType);
  }

  /**
   * Returns a {@link TypeToken} that represents the type {@code HandlerDelegatorContext<UserHandlerClass>}.
   */
  private <T extends HttpServiceHandler> TypeToken<DelegatorContext<T>> getContextType(TypeToken<T> delegateType) {
    return new TypeToken<DelegatorContext<T>>() {
    }.where(new TypeParameter<T>() { }, delegateType);
  }

  /**
   * Generates the class signature of the generate class. The generated class is not parameterized, however
   * it extends from {@link AbstractHttpHandlerDelegator} with parameterized type of the user http handler.
   *
   * @param delegateType Type of the user http handler
   * @return The signature string
   */
  private String getClassSignature(TypeToken<?> delegateType) {
    SignatureWriter writer = new SignatureWriter();

    // Construct the superclass signature as "AbstractHttpHandlerDelegator<UserHandlerClass>"
    SignatureVisitor sv = writer.visitSuperclass();
    sv.visitClassType(Type.getInternalName(AbstractHttpHandlerDelegator.class));
    SignatureVisitor tv = sv.visitTypeArgument('=');
    tv.visitClassType(Type.getInternalName(delegateType.getRawType()));
    tv.visitEnd();
    sv.visitEnd();

    return writer.toString();
  }

  /**
   * The ASM MethodVisitor for visiting handler class methods and optionally copy them if it is a handler
   * method.
   */
  private class HandlerMethodVisitor extends MethodVisitor {

    private final TypeToken<?> delegateType;
    private final List<AnnotationNode> annotations;
    private final ListMultimap<Integer, AnnotationNode> paramAnnotations;
    private final String desc;
    private final String signature;
    private final int access;
    private final String name;
    private final String[] exceptions;
    private final Type classType;
    private final ClassWriter classWriter;
    private final List<Class<?>> preservedClasses;

    /**
     * Constructs a {@link HandlerMethodVisitor}.
     *
     * @param delegateType The user handler type
     * @param mv The {@link MethodVisitor} to delegate calls to if not handled by this class
     * @param desc Method description
     * @param signature Method signature
     * @param access Method access flag
     * @param name Method name
     * @param exceptions Method exceptions list
     * @param classType Type of the generated class
     * @param classWriter Writer for generating bytecode
     * @param preservedClasses List for storing classes that needs to be preserved for correct class loading.
     *                         See {@link ClassDefinition} for details.
     */
    HandlerMethodVisitor(TypeToken<?> delegateType, MethodVisitor mv, String desc,
                         String signature, int access, String name, String[] exceptions,
                         Type classType, ClassWriter classWriter, List<Class<?>> preservedClasses) {
      super(Opcodes.ASM5, mv);
      this.delegateType = delegateType;
      this.desc = desc;
      this.signature = signature;
      this.access = access;
      this.name = name;
      this.exceptions = exceptions;
      this.annotations = Lists.newArrayList();
      this.paramAnnotations = LinkedListMultimap.create();
      this.classType = classType;
      this.classWriter = classWriter;
      this.preservedClasses = preservedClasses;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      // Memorize all visible annotations
      if (visible) {
        AnnotationNode annotationNode = new AnnotationNode(Opcodes.ASM5, desc);
        annotations.add(annotationNode);
        return annotationNode;
      }
      return super.visitAnnotation(desc, visible);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      // Memorize all visible annotations for each parameter.
      // It needs to store in a Multimap because there can be multiple annotations per parameter.
      if (visible) {
        AnnotationNode annotationNode = new AnnotationNode(Opcodes.ASM5, desc);
        paramAnnotations.put(parameter, annotationNode);
        return annotationNode;
      }
      return super.visitParameterAnnotation(parameter, desc, visible);
    }

    @Override
    public void visitEnd() {
      // If any annotations of the method is one of those HttpMethod,
      // this is a handler process, hence need to copy.
      boolean handlerMethod = false;
      TransactionControl txCtrl = TransactionControl.IMPLICIT;
      for (AnnotationNode annotation : annotations) {
        if (isHandlerMethod(Type.getType(annotation.desc))) {
          handlerMethod = true;
        } else if (TX_POLICY_TYPE.equals(Type.getType(annotation.desc))) {
          List list = annotation.values;
          for (Iterator iter = list.iterator(); iter.hasNext(); ) {
            if ("value".equals(iter.next()) && iter.hasNext()) {
              Object value = iter.next();
              if (String[].class.equals(value.getClass())) {
                String[] values = (String[]) value;
                if (values.length == 2 && TX_CONTROL_TYPE.equals(Type.getType(values[0]))) {
                  txCtrl = TransactionControl.valueOf(values[1]);
                }
              }
            }
          }
        }
      }

      if (!handlerMethod) {
        super.visitEnd();
        return;
      }

      Type returnType = Type.getReturnType(desc);
      Type[] argTypes = Type.getArgumentTypes(desc);

      // If the first two parameters are not HttpServiceRequest and HttpServiceResponder, don't copy
      if (argTypes.length < 2
        || !argTypes[0].equals(Type.getType(HttpServiceRequest.class))
        || !argTypes[1].equals(Type.getType(HttpServiceResponder.class))) {
        super.visitEnd();
        return;
      }

      argTypes[0] = Type.getType(HttpRequest.class);
      argTypes[1] = Type.getType(HttpResponder.class);

      preserveParameterClasses(argTypes);

      // If the return type is an instance of HttpContentConsumer, the generated method need to have
      // netty-http BodyConsumer as return type.
      if (returnType.getSort() == Type.OBJECT) {
        try {
          Class<?> returnClass = delegateType.getRawType().getClassLoader().loadClass(returnType.getClassName());
          if (HttpContentConsumer.class.isAssignableFrom(returnClass)) {
            returnType = Type.getType(BodyConsumer.class);
          }
        } catch (ClassNotFoundException e) {
          // Shouldn't happen since the delegateType (user handler class) is already loaded and the method return
          // type should be loadable through the same classloader
          throw Throwables.propagate(e);
        }
      }

      // Copy the method signature with the first two parameter types changed and return type changed
      String methodDesc = Type.getMethodDescriptor(returnType, argTypes);
      MethodVisitor methodVisitor = classWriter.visitMethod(access, name, methodDesc,
                                                            rewriteMethodSignature(signature), exceptions);
      final GeneratorAdapter mg = new GeneratorAdapter(methodVisitor, access, name, desc);

      // Replay all annotations before generating the body.
      for (AnnotationNode annotation : annotations) {
        annotation.accept(mg.visitAnnotation(annotation.desc, true));
      }
      // Replay all parameter annotations
      for (Map.Entry<Integer, AnnotationNode> entry : paramAnnotations.entries()) {
        AnnotationNode annotation = entry.getValue();
        annotation.accept(mg.visitParameterAnnotation(entry.getKey(), annotation.desc, true));
      }

      // Each request method is wrapped by a transaction lifecycle.
      generateTransactionalDelegateBody(mg, new Method(name, desc), txCtrl);

      super.visitEnd();
    }

    /**
     * Preserves method parameter classes for class loading. The first two parameters are always
     * {@link HttpServiceRequest} and {@link HttpServiceResponder}, which don't need to be preserved since
     * they are always loaded by the CDAP system ClassLoader. The third and onward method parameter classes
     * need to be preserved since they can be defined by user, hence in the user ClassLoader.
     *
     * @see ClassDefinition
     */
    private void preserveParameterClasses(Type[] argTypes) {
      if (argTypes.length <= 2) {
        return;
      }
      try {
        for (int i = 2; i < argTypes.length; i++) {
          // Only add object type parameter which is not Java class
          if (argTypes[i].getSort() == Type.OBJECT) {
            Class<?> cls = delegateType.getRawType().getClassLoader().loadClass(argTypes[i].getClassName());

            // Classes loaded by bootstrap classloader are having null ClassLoader. They don't need to be preserved.
            if (cls.getClassLoader() != null) {
              preservedClasses.add(cls);
            }
          }
        }
      } catch (ClassNotFoundException e) {
        // Shouldn't happen, as the parameter class should be loading from the user handler ClassLoader
        throw Throwables.propagate(e);
      }
    }

    /**
     * Rewrite the handler method signature to have the first two parameters rewritten from
     * {@link HttpServiceRequest} and {@link HttpServiceResponder} into
     * {@link HttpRequest} and {@link HttpResponder}.
     */
    private String rewriteMethodSignature(String signature) {
      if (signature == null) {
        return null;
      }

      SignatureReader reader = new SignatureReader(signature);
      SignatureWriter writer = new SignatureWriter() {
        @Override
        public void visitClassType(String name) {
          if (name.equals(Type.getInternalName(HttpServiceRequest.class))) {
            super.visitClassType(Type.getInternalName(HttpRequest.class));
            return;
          }
          if (name.equals(Type.getInternalName(HttpServiceResponder.class))) {
            super.visitClassType(Type.getInternalName(HttpResponder.class));
            return;
          }
          super.visitClassType(name);
        }
      };
      reader.accept(writer);

      return writer.toString();
    }

    /**
     * Wrap the user written Handler method in a transaction.
     * The transaction begins before calling the user method, and commit after the user method returns.
     * On errors the transaction is aborted and rolledback.
     *
     * The generated handler method body has the form:
     *
     * <pre>{@code
     *   public void|BodyConsumer handle(HttpRequest request, HttpResponder responder, ...) {
     *     T handler = getHandler();
     *     HttpContentConsumer contentConsumer = null;
     *     DelayedHttpServiceResponder wrappedResponder = wrapResponder(responder);
     *     try {
     *       // only start tx if transaction control is IMPLICIT
     *       TransactionContext txContext = startTransactionContext();
     *       // only generate this try catch block if transaction control is IMPLICIT
     *       try {
     *         ClassLoader classLoader = ClassLoaders.setContextClassLoader(createHandlerContextClassLoader());
     *         try {
     *           // Only do assignment if handler method returns HttpContentConsumer
     *           [contentConsumer = ]handler.handle(wrapRequest(request), wrappedResponder, ...);
     *         } finally {
     *           ClassLoaders.setContextClassLoader(classLoader);
     *         }
     *       // only generate this try catch block if transaction control is IMPLICIT
     *       } catch (Throwable t) {
     *         LOG.error("User handler exception", t);
     *         txContext.abort(new TransactionFailureException("User handler exception", t));
     *       }
     *       // only finish tx if transaction control is IMPLICIT
     *       txContext.finish();
     *     } catch (TransactionFailureException e) { // generate only if transaction constrol is IMPLICIT
     *        LOG.error("Transaction failure: ", e);
     *        wrappedResponder.setTransactionFailureResponse(e);
     *        contentConsumer = null;
     *     } catch (Throwable t) { // generate only if transaction constrol is EXPLICIT
     *        LOG.error("User Handler exception", t);
     *        wrappedResponder.setTransactionFailureResponse(t);
     *        contentConsumer = null;
     *     }
     *     if (contentConsumer == null) {
     *       wrappedResponder.execute();
     *       // Only return null if handler method returns HttpContentConsumer
     *       [return null;]
     *     }
     *
     *     // Only generated if handler method returns HttpContentConsumer
     *     [return wrapContentConsumer(httpContentConsumer, wrappedResponder);]
     *   }
     * }
     * </pre>
     */
    private void generateTransactionalDelegateBody(GeneratorAdapter mg, Method method, TransactionControl txCtrl) {
      Type handlerType = Type.getType(delegateType.getRawType());
      Type txContextType = Type.getType(TransactionContext.class);
      Type txFailureExceptionType = Type.getType(TransactionFailureException.class);
      Type loggerType = Type.getType(Logger.class);
      Type throwableType = Type.getType(Throwable.class);
      Type delayedHttpServiceResponderType = Type.getType(DelayedHttpServiceResponder.class);
      Type httpContentConsumerType = Type.getType(HttpContentConsumer.class);

      Label txTryBegin = mg.newLabel();
      Label txTryEnd = mg.newLabel();
      Label txCatch = mg.newLabel();
      Label txFinish = mg.newLabel();

      Label handlerTryBegin = mg.newLabel();
      Label handlerTryEnd = mg.newLabel();
      Label handlerCatch = mg.newLabel();
      Label handlerFinish = mg.newLabel();

      if (TransactionControl.IMPLICIT == txCtrl) {
        mg.visitTryCatchBlock(txTryBegin, txTryEnd, txCatch, txFailureExceptionType.getInternalName());
        mg.visitTryCatchBlock(handlerTryBegin, handlerTryEnd, handlerCatch, throwableType.getInternalName());
      } else {
        mg.visitTryCatchBlock(txTryBegin, txTryEnd, txCatch, throwableType.getInternalName());
      }

      // T handler = getHandler();
      int handler = mg.newLocal(handlerType);
      mg.loadThis();
      mg.invokeVirtual(classType,
                       Methods.getMethod(HttpServiceHandler.class, "getHandler"));
      mg.checkCast(handlerType);
      mg.storeLocal(handler, handlerType);

      // HttpContentConsumer contentConsumer = null;
      int contentConsumer = mg.newLocal(httpContentConsumerType);
      mg.visitInsn(Opcodes.ACONST_NULL);
      mg.storeLocal(contentConsumer, httpContentConsumerType);

      // DelayedHttpServiceResponder wrappedResponder = wrapResponder(responder);;
      int wrappedResponder = mg.newLocal(delayedHttpServiceResponderType);
      mg.loadThis();
      mg.loadArg(1);
      mg.invokeVirtual(classType,
                       Methods.getMethod(DelayedHttpServiceResponder.class, "wrapResponder", HttpResponder.class));
      mg.storeLocal(wrappedResponder, delayedHttpServiceResponderType);

      // try {  // Outer try for transaction failure
      mg.mark(txTryBegin);

      // only start tx if transaction control is IMPLICIT
      int txContext = 0;
      if (TransactionControl.IMPLICIT == txCtrl) {

        // TransactionContext txContext = startTransactionContext();
        txContext = mg.newLocal(txContextType);
        mg.loadThis();
        mg.invokeVirtual(classType,
                         Methods.getMethod(TransactionContext.class, "startTransactionContext"));
        mg.storeLocal(txContext, txContextType);

        // only generate this try catch block if transaction control is IMPLICIT
        // try { // Inner try for user handler failure
        mg.mark(handlerTryBegin);
      }
      // If no body consumer, generates:
      // this.getHandler(wrapRequest(request), wrappedResponder, ...);
      //
      // otherwise, generates:
      // contentConsumer = this.getHandler(wrapRequest(reuqest), wrappedResponder, ...);
      generateInvokeDelegate(mg, handler, method, wrappedResponder);
      if (method.getReturnType().getSort() == Type.OBJECT) {
        mg.storeLocal(contentConsumer, httpContentConsumerType);
      }

      if (TransactionControl.IMPLICIT == txCtrl) {
        // } // end of inner try
        mg.mark(handlerTryEnd);
        mg.goTo(handlerFinish);

        // } catch (Throwable t) {  // inner try-catch
        mg.mark(handlerCatch);
        int throwable = mg.newLocal(throwableType);
        mg.storeLocal(throwable);

        // LOG.error("User handler exception", e);
        mg.getStatic(classType, "LOG", loggerType);
        mg.visitLdcInsn("User handler exception: ");
        mg.loadLocal(throwable);
        mg.invokeInterface(loggerType, Methods.getMethod(void.class, "error", String.class, Throwable.class));

        // transactionContext.abort(new TransactionFailureException("User handler exception: ", e));
        mg.loadLocal(txContext, txContextType);
        mg.newInstance(txFailureExceptionType);
        mg.dup();
        mg.visitLdcInsn("User handler exception: ");
        mg.loadLocal(throwable);
        mg.invokeConstructor(txFailureExceptionType,
                             Methods.getMethod(void.class, "<init>", String.class, Throwable.class));
        mg.invokeVirtual(txContextType, Methods.getMethod(void.class, "abort", TransactionFailureException.class));

        // } // end of inner catch
        mg.mark(handlerFinish);

        // only finish tx if transaction control is IMPLICIT
        // txContext.finish()
        mg.loadLocal(txContext, txContextType);
        mg.invokeVirtual(txContextType, Methods.getMethod(void.class, "finish"));
      }
      mg.goTo(txTryEnd);

      // } // end of outer try
      mg.mark(txTryEnd);
      mg.goTo(txFinish);

      // } catch
      mg.mark(txCatch);

      // } catch (TransactionFailureException e) {  // IMPLICIT
      // } catch (Throwable t) { // EXPLICIT

      Type caughtType = TransactionControl.IMPLICIT == txCtrl ? txFailureExceptionType : throwableType;
      String message = TransactionControl.IMPLICIT == txCtrl ? "Transaction Failure: " : "User Handler failure: ";

      int throwable = mg.newLocal(caughtType);
      mg.storeLocal(throwable, caughtType);

      // LOG.error("Transaction failure: ", e);
      mg.getStatic(classType, "LOG", loggerType);
      mg.visitLdcInsn(message);
      mg.loadLocal(throwable);
      mg.invokeInterface(loggerType, Methods.getMethod(void.class, "error", String.class,
                                                       Throwable.class));

      // wrappedResponder.setTransactionFailureResponse(e);
      mg.loadLocal(wrappedResponder);
      mg.loadLocal(throwable);
      mg.invokeVirtual(delayedHttpServiceResponderType, Methods.getMethod(void.class, "setTransactionFailureResponse",
                                                                          Throwable.class));

      // contentConsumer = null;
      mg.visitInsn(Opcodes.ACONST_NULL);
      mg.storeLocal(contentConsumer);

      // } // end of outer catch
      mg.mark(txFinish);

      // If body consumer is used, generates:
      //
      // if (httpContentConsumer == null) {
      //   wrappedResponder.execute();
      //   return null;
      // }
      // return wrapContentConsumer(httpContentConsumer, wrappedResponder);
      //
      // Otherwise, generates
      // wrappedResponder.execute();
      if (method.getReturnType().getSort() == Type.OBJECT) {
        Label hasContentConsumer = mg.newLabel();
        mg.loadLocal(contentConsumer);

        // if contentConsumer != null, goto label hasContentConsumer
        mg.ifNonNull(hasContentConsumer);

        //   wrappedResponder.execute();
        //   return null;
        mg.loadLocal(wrappedResponder);
        mg.invokeVirtual(delayedHttpServiceResponderType, Methods.getMethod(void.class, "execute"));
        mg.visitInsn(Opcodes.ACONST_NULL);
        mg.returnValue();

        mg.mark(hasContentConsumer);

        // IMPORTANT: If body consumer is used, calling wrapContentConsumer must be
        // the last thing to do in this generated method since the current context will be captured
        // return wrapContentConsumer(httpContentConsumer, wrappedResponder);
        mg.loadThis();
        mg.loadLocal(contentConsumer);
        mg.loadLocal(wrappedResponder);
        mg.invokeVirtual(classType, Methods.getMethod(BodyConsumer.class, "wrapContentConsumer",
                                                      HttpContentConsumer.class,
                                                      DelayedHttpServiceResponder.class));
        mg.returnValue();
      } else {
        // wrappedResponder.execute()
        mg.loadLocal(wrappedResponder);
        mg.invokeVirtual(delayedHttpServiceResponderType, Methods.getMethod(void.class, "execute"));

        mg.returnValue();
      }

      mg.endMethod();
    }

    /**
     * Generates the code block for setting context ClassLoader, calling user handler method
     * and resetting context ClassLoader.
     */
    private void generateInvokeDelegate(GeneratorAdapter mg, int handler, Method method, int responder) {
      Type classLoaderType = Type.getType(ClassLoader.class);
      Type handlerType = Type.getType(delegateType.getRawType());

      Label contextTryBegin = mg.newLabel();
      Label contextTryEnd = mg.newLabel();
      Label contextCatch = mg.newLabel();
      Label contextEnd = mg.newLabel();
      Label contextCatchEnd = mg.newLabel();

      // For the try-finally block
      // In bytecode, it's done by two try-catch instructions. The finally code are in both
      // the tryEnd and catchEnd blocks.
      mg.visitTryCatchBlock(contextTryBegin, contextTryEnd, contextCatch, null);
      mg.visitTryCatchBlock(contextCatch, contextCatchEnd, contextCatch, null);

      int throwable = mg.newLocal(Type.getType(Throwable.class));

      // ClassLoader classLoader = ClassLoaders.setContextClassLoader(createHandlerContextClassLoader());
      int classLoader = mg.newLocal(classLoaderType);
      mg.loadThis();
      mg.invokeVirtual(classType, Methods.getMethod(ClassLoader.class, "createHandlerContextClassLoader"));
      mg.invokeStatic(Type.getType(ClassLoaders.class),
                      Methods.getMethod(ClassLoader.class, "setContextClassLoader", ClassLoader.class));
      mg.storeLocal(classLoader, classLoaderType);

      // try {
      mg.mark(contextTryBegin);

      // handler.method(wrapRequest(request), responder, ...);
      mg.loadLocal(handler);

      mg.loadThis();
      mg.loadArg(0);
      mg.invokeVirtual(classType,
                       Methods.getMethod(HttpServiceRequest.class, "wrapRequest", HttpRequest.class));

      mg.loadLocal(responder);

      for (int i = 2; i < method.getArgumentTypes().length; i++) {
        mg.loadArg(i);
      }

      mg.invokeVirtual(Type.getType(delegateType.getRawType()), method);

      // }
      mg.mark(contextTryEnd);

      // Reset ClassLoader, like in finally {}
      // ClassLoaders.setContextClassLoader(classLoader);
      setClassLoader(mg, classLoader);
      mg.goTo(contextEnd);

      // } catch and rethrow
      mg.mark(contextCatch);
      mg.storeLocal(throwable);

      mg.mark(contextCatchEnd);

      // A duplicate of finally block is needed in bytecode so that it gets executed when there is exception
      // ClassLoaders.setContextClassLoader(classLoader);
      setClassLoader(mg, classLoader);
      mg.loadLocal(throwable);
      mg.throwException();

      mg.mark(contextEnd);
    }

    private void setClassLoader(GeneratorAdapter mg, int classLoader) {
      mg.loadLocal(classLoader);
      mg.invokeStatic(Type.getType(ClassLoaders.class),
                      Methods.getMethod(ClassLoader.class, "setContextClassLoader", ClassLoader.class));
      mg.pop();
    }
  }
}
