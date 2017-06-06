/*
 * Copyright © 2014-2016 Cask Data, Inc.
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
package co.cask.cdap.internal.specification;

import co.cask.cdap.api.annotation.Batch;
import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.annotation.Tick;
import co.cask.cdap.api.flow.FlowletDefinition;
import co.cask.cdap.api.flow.flowlet.InputContext;
import co.cask.cdap.internal.guava.reflect.TypeToken;
import co.cask.cdap.internal.lang.MethodVisitor;
import co.cask.cdap.internal.lang.Reflections;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public final class ProcessMethodExtractor extends MethodVisitor {

  private final Map<String, Set<Type>> inputTypes;
  private final Set<FlowletMethod> seenMethods;

  public ProcessMethodExtractor(Map<String, Set<Type>> inputTypes) {
    this.inputTypes = inputTypes;
    this.seenMethods = new HashSet<>();
  }

  @Override
  public void visit(Object instance, Type inspectType, Type declareType, Method method) throws Exception {
    if (!seenMethods.add(FlowletMethod.create(method, inspectType))) {
      // The method is already seen. It can only happen if a children class override a parent class method and
      // is visting the parent method, since the method visiting order is always from the leaf class walking
      // up the class hierarchy.
      return;
    }

    ProcessInput processInputAnnotation = method.getAnnotation(ProcessInput.class);
    Tick tickAnnotation = method.getAnnotation(Tick.class);
    TypeToken<?> inspectTypeToken = TypeToken.of(inspectType);

    if (processInputAnnotation == null && tickAnnotation == null) {
      return;
    }

    // Check for tick method
    if (tickAnnotation != null) {
      checkArgument(processInputAnnotation == null,
                    "Tick method %s.%s should not have ProcessInput.",
                    inspectTypeToken.getRawType().getName(), method);
      checkArgument(method.getParameterTypes().length == 0,
                    "Tick method %s.%s cannot have parameters.",
                    inspectTypeToken.getRawType().getName(), method);
      return;
    }

    Type[] methodParams = method.getGenericParameterTypes();
    checkArgument(methodParams.length > 0 && methodParams.length <= 2,
                  "Parameter missing from process method %s.%s.",
                  inspectTypeToken.getRawType().getName(), method);

    // If there is more than one parameter there can only be exactly two; the second one must be InputContext type
    if (methodParams.length == 2) {
      checkArgument(InputContext.class.equals(TypeToken.of(methodParams[1]).getRawType()),
                    "Second parameter must be InputContext type for process method %s.%s.",
                    inspectTypeToken.getRawType().getName(), method);
    }

    // Extract the Input type from the first parameter of the process method
    Type inputType = getInputType(inspectTypeToken, method, inspectTypeToken.resolveType(methodParams[0]).getType());
    checkArgument(Reflections.isResolved(inputType),
                  "Invalid type in %s.%s. Only Class or ParameterizedType are supported.",
                  inspectTypeToken.getRawType().getName(), method);

    List<String> inputNames = new LinkedList<>();
    if (processInputAnnotation.value().length == 0) {
      inputNames.add(FlowletDefinition.ANY_INPUT);
    } else {
      Collections.addAll(inputNames, processInputAnnotation.value());
    }

    for (String inputName : inputNames) {
      Set<Type> types = inputTypes.get(inputName);
      if (types == null) {
        types = new HashSet<>();
        inputTypes.put(inputName, types);
      }
      checkArgument(types.add(inputType),
                    "Same type already defined for the same input name %s in process method %s.%s.",
                    inputName, inspectTypeToken.getRawType().getName(), method);
    }
  }

  private Type getInputType(TypeToken<?> type, Method method, Type methodParam) {
    // In batch mode, if the first parameter is an iterator then extract the type information from
    // the iterator's type parameter
    if (method.getAnnotation(Batch.class) != null) {
      if (methodParam instanceof ParameterizedType) {
        ParameterizedType pType = (ParameterizedType) methodParam;
        if (pType.getRawType().equals(Iterator.class)) {
          methodParam = pType.getActualTypeArguments()[0];
        }
      }
    } else {
      // Check to see if there is an method param which is a type of iterator.
      // This check is needed because we don't support type projection with iterator.
      if (methodParam instanceof ParameterizedType) {
        ParameterizedType pType = (ParameterizedType) methodParam;
        checkArgument(!pType.getRawType().equals(Iterator.class),
                      "Iterator type should only be used with Batch annotation for process method %s.%s",
                      type.getRawType().getName(), method.getName());
      }
    }
    return methodParam;
  }

  private void checkArgument(boolean condition, String template, Object...args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(template, args));
    }
  }
}
