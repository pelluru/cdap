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

package co.cask.cdap.hive.objectinspector;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorUtils;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ObjectInspectorFactory is the primary way to create new ObjectInspector
 * instances.
 * <p/>
 * SerDe classes should call the static functions in this library to create an
 * ObjectInspector to return to the caller of SerDe2.getObjectInspector().
 * <p/>
 * The reason of having caches here is that ObjectInspectors
 * do not have an internal state - so ObjectInspectors with the
 * same construction parameters should result in exactly the same
 * ObjectInspector.
 */
public final class ObjectInspectorFactory {

  private static ConcurrentHashMap<Type, ObjectInspector> objectInspectorCache =
      new ConcurrentHashMap<>();

  public static ObjectInspector getReflectionObjectInspector(Type t) {
    ObjectInspector oi = objectInspectorCache.get(t);
    if (oi == null) {
      oi = getReflectionObjectInspectorNoCache(t);
      objectInspectorCache.put(t, oi);
    }
    return oi;
  }

  private static ObjectInspector getReflectionObjectInspectorNoCache(Type t) {
    if (t instanceof GenericArrayType) {
      GenericArrayType at = (GenericArrayType) t;
      return getStandardListObjectInspector(getReflectionObjectInspector(at.getGenericComponentType()));
    }

    Map<TypeVariable, Type> genericTypes = null;
    if (t instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) t;
      Type rawType = pt.getRawType();
      // Collection?
      if (Collection.class.isAssignableFrom((Class<?>) rawType)) {
        return getStandardListObjectInspector(getReflectionObjectInspector(pt.getActualTypeArguments()[0]));
      }
      // Map?
      if (Map.class.isAssignableFrom((Class<?>) rawType)) {
        return getStandardMapObjectInspector(getReflectionObjectInspector(pt.getActualTypeArguments()[0]),
                                             getReflectionObjectInspector(pt.getActualTypeArguments()[1]));
      }
      // Otherwise convert t to RawType so we will fall into the following if block.
      t = rawType;

      ImmutableMap.Builder<TypeVariable, Type> builder = ImmutableMap.builder();
      for (int i = 0; i < pt.getActualTypeArguments().length; i++) {
        builder.put(((Class<?>) t).getTypeParameters()[i], pt.getActualTypeArguments()[i]);
      }
      genericTypes = builder.build();
    }

    // Must be a class.
    if (!(t instanceof Class)) {
      throw new RuntimeException(ObjectInspectorFactory.class.getName() + " internal error:" + t);
    }
    Class<?> c = (Class<?>) t;

    // Java Primitive Type?
    if (PrimitiveObjectInspectorUtils.isPrimitiveJavaType(c)) {
      return PrimitiveObjectInspectorFactory.getPrimitiveJavaObjectInspector(
          PrimitiveObjectInspectorUtils.getTypeEntryFromPrimitiveJavaType(c).primitiveCategory);
    }

    // Java Primitive Class?
    if (PrimitiveObjectInspectorUtils.isPrimitiveJavaClass(c)) {
      return PrimitiveObjectInspectorFactory.getPrimitiveJavaObjectInspector(
          PrimitiveObjectInspectorUtils.getTypeEntryFromPrimitiveJavaClass(c).primitiveCategory);
    }

    // Primitive Writable class?
    if (PrimitiveObjectInspectorUtils.isPrimitiveWritableClass(c)) {
      return PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(
          PrimitiveObjectInspectorUtils.getTypeEntryFromPrimitiveWritableClass(c).primitiveCategory);
    }

    // Enum class?
    if (Enum.class.isAssignableFrom(c)) {
      return PrimitiveObjectInspectorFactory.getPrimitiveJavaObjectInspector(
          PrimitiveObjectInspector.PrimitiveCategory.STRING);
    }

    // Array
    if (c.isArray()) {
      return getStandardListObjectInspector(getReflectionObjectInspector(c.getComponentType()));
    }

    // Must be struct because List and Map need to be ParameterizedType
    Preconditions.checkState(!List.class.isAssignableFrom(c));
    Preconditions.checkState(!Map.class.isAssignableFrom(c));

    Preconditions.checkState(!c.isInterface(), "Cannot inspect an interface.");

    ReflectionStructObjectInspector oi = new ReflectionStructObjectInspector();
    // put it into the cache BEFORE it is initialized to make sure we can catch
    // recursive types.
    objectInspectorCache.put(t, oi);
    Field[] fields = ObjectInspectorUtils.getDeclaredNonStaticFields(c);
    List<ObjectInspector> structFieldObjectInspectors = new ArrayList<>(fields.length);
    for (Field field : fields) {
      // Exclude transient fields and synthetic fields. The latter has the effect of excluding the implicit
      // "this" pointer present in nested classes and that references the parent.
      if (Modifier.isTransient(field.getModifiers()) || field.isSynthetic()) {
        continue;
      }
      if (!oi.shouldIgnoreField(field.getName())) {
        Type newType = field.getGenericType();
        if (newType instanceof TypeVariable) {
          Preconditions.checkNotNull(genericTypes, "Type was not recognized as a parameterized type.");
          Preconditions.checkNotNull(genericTypes.get(newType),
                                     "Generic type " + newType + " not a parameter of class " + c);
          newType = genericTypes.get(newType);
        }
        structFieldObjectInspectors.add(getReflectionObjectInspector(newType));
      }
    }
    oi.init(c, structFieldObjectInspectors);
    return oi;
  }

  static ConcurrentHashMap<ObjectInspector, StandardListObjectInspector> cachedStandardListObjectInspector =
      new ConcurrentHashMap<>();

  public static StandardListObjectInspector getStandardListObjectInspector(ObjectInspector listElementObjectInspector) {
    StandardListObjectInspector result = cachedStandardListObjectInspector.get(listElementObjectInspector);
    if (result == null) {
      result = new StandardListObjectInspector(listElementObjectInspector);
      cachedStandardListObjectInspector.put(listElementObjectInspector, result);
    }
    return result;
  }

  static ConcurrentHashMap<List<ObjectInspector>, StandardMapObjectInspector> cachedStandardMapObjectInspector =
      new ConcurrentHashMap<>();

  public static StandardMapObjectInspector getStandardMapObjectInspector(ObjectInspector mapKeyObjectInspector,
                                                                         ObjectInspector mapValueObjectInspector) {
    List<ObjectInspector> signature = ImmutableList.of(mapKeyObjectInspector, mapValueObjectInspector);
    StandardMapObjectInspector result = cachedStandardMapObjectInspector.get(signature);
    if (result == null) {
      result = new StandardMapObjectInspector(mapKeyObjectInspector, mapValueObjectInspector);
      cachedStandardMapObjectInspector.put(signature, result);
    }
    return result;
  }

  static ConcurrentHashMap<List<ObjectInspector>, StandardUnionObjectInspector>
      cachedStandardUnionObjectInspector =
      new ConcurrentHashMap<>();

  public static StandardUnionObjectInspector getStandardUnionObjectInspector(
      List<ObjectInspector> unionObjectInspectors) {
    StandardUnionObjectInspector result = cachedStandardUnionObjectInspector
        .get(unionObjectInspectors);
    if (result == null) {
      result = new StandardUnionObjectInspector(unionObjectInspectors);
      cachedStandardUnionObjectInspector.put(unionObjectInspectors, result);
    }
    return result;
  }

  static ConcurrentHashMap<ArrayList<List<?>>, StandardStructObjectInspector> cachedStandardStructObjectInspector =
      new ConcurrentHashMap<>();

  public static StandardStructObjectInspector getStandardStructObjectInspector(
      List<String> structFieldNames,
      List<ObjectInspector> structFieldObjectInspectors) {
    return getStandardStructObjectInspector(structFieldNames, structFieldObjectInspectors, null);
  }

  public static StandardStructObjectInspector getStandardStructObjectInspector(
      List<String> structFieldNames,
      List<ObjectInspector> structFieldObjectInspectors,
      List<String> structComments) {
    ArrayList<List<?>> signature = new ArrayList<>(3);
    signature.add(structFieldNames);
    signature.add(structFieldObjectInspectors);
    if (structComments != null) {
      signature.add(structComments);
    }
    StandardStructObjectInspector result = cachedStandardStructObjectInspector.get(signature);
    if (result == null) {
      result = new StandardStructObjectInspector(structFieldNames, structFieldObjectInspectors, structComments);
      cachedStandardStructObjectInspector.put(signature, result);
    }
    return result;
  }

  static ConcurrentHashMap<List<StructObjectInspector>, UnionStructObjectInspector> cachedUnionStructObjectInspector =
      new ConcurrentHashMap<>();

  public static UnionStructObjectInspector getUnionStructObjectInspector(
      List<StructObjectInspector> structObjectInspectors) {
    UnionStructObjectInspector result = cachedUnionStructObjectInspector
        .get(structObjectInspectors);
    if (result == null) {
      result = new UnionStructObjectInspector(structObjectInspectors);
      cachedUnionStructObjectInspector.put(structObjectInspectors, result);
    }
    return result;
  }

  private ObjectInspectorFactory() {
    // prevent instantiation
  }

}

