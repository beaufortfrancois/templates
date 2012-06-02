// Copyright 2012 Benjamin Kalman
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.lman.json;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lman.common.Transient;

/**
 * A JSON view over an immutable Java object.
 *
 */
public class PojoJsonView implements JsonView {

  private final Object pojo;

  // Lazily-determined type.
  private Type type = null;

  // Lazily-created cache of found values via get().
  // TODO: limit the size of this cache.
  private Map<String, JsonView> foundViaGet = null;

  private boolean isTransient = false;

  // Lazily-created cache of not-found keys via get().
  // TODO: why does this make things run slower?
  //private Set<String> notFoundViaGet = null;

  public PojoJsonView(Object pojo) {
    this.pojo = pojo;
  }

  public PojoJsonView(Object pojo, Annotation[] annotations) {
    this.pojo = pojo;
    for (Annotation annotation : annotations) {
      if (annotation instanceof Transient) {
        this.isTransient = true;
      }
    }
  }

  @Override
  public Type getType() {
    if (type == null) {
      if (pojo == null) {
        type = Type.NULL;
      } else if (Boolean.class.isAssignableFrom(pojo.getClass())) {
        type = Type.BOOLEAN;
      } else if (Number.class.isAssignableFrom(pojo.getClass())) {
        type = Type.NUMBER;
      } else if (Enum.class.isAssignableFrom(pojo.getClass()) ||
                 String.class.isAssignableFrom(pojo.getClass())) {
        type = Type.STRING;
      } else if (pojo.getClass().isArray() ||
                 Collection.class.isAssignableFrom(pojo.getClass())) {
        type = Type.ARRAY;
      } else {
        type = Type.OBJECT;
      }
    }
    return type;
  }

  @Override
  public <E> E asInstance(Class<E> clazz) {
    if (clazz.isAssignableFrom(pojo.getClass()))
      return clazz.cast(pojo);
    else
      return null;
  }

  @Override
  public boolean isNull() {
    return getType() == Type.NULL;
  }

  @Override
  public boolean asBoolean() {
    checkIsType(Type.BOOLEAN);
    return ((Boolean) pojo).booleanValue();
  }

  @Override
  public Number asNumber() {
    checkIsType(Type.NUMBER);
    return (Number) pojo;
  }

  @Override
  public String asString() {
    checkIsType(Type.STRING);
    if (Enum.class.isAssignableFrom(pojo.getClass()))
      return ((Enum<?>) pojo).name();
    else if (String.class.isAssignableFrom(pojo.getClass()))
      return (String) pojo;
    else
      throw new AssertionError();
  }

  @Override
  public boolean asArrayIsEmpty() {
    checkIsType(Type.ARRAY);
    if (pojo.getClass().isArray())
      return ((Object[]) pojo).length == 0;
    else if (Collection.class.isAssignableFrom(pojo.getClass()))
      return ((Collection<?>) pojo).isEmpty();
    else
      throw new AssertionError();
  }

  @Override
  public void asArrayForeach(ArrayVisitor visitor) {
    checkIsType(Type.ARRAY);
    if (pojo.getClass().isArray()) {
      Object[] array = (Object[]) pojo;
      for (int i = 0; i < array.length; i++)
        visitor.visit(new PojoJsonView(array[i]), i);
    } else if (Collection.class.isAssignableFrom(pojo.getClass())) {
      Collection<?> collection = (Collection<?>) pojo;
      int i = 0;
      for (Object value : collection)
        visitor.visit(new PojoJsonView(value), i++);
    } else {
      throw new AssertionError();
    }
  }

  @Override
  public boolean asObjectIsEmpty() {
    checkIsType(Type.OBJECT);
    if (Map.class.isAssignableFrom(pojo.getClass())) {
      return ((Map<?, ?>) pojo).isEmpty();
    } else {
      for (Field f : pojo.getClass().getFields()) {
        if (Modifier.isStatic(f.getModifiers()))
          continue;
        return false;
      }
      return true;
    }
  }

  @Override
  public void asObjectForeach(ObjectVisitor visitor) {
    checkIsType(Type.OBJECT);
    // TODO: maybe try caching reflection stuff as a map?
    if (Map.class.isAssignableFrom(pojo.getClass())) {
      Map<?, ?> map = (Map<?, ?>) pojo;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!String.class.isAssignableFrom(entry.getKey().getClass()))
          throw new UnsupportedOperationException("Illegal key type: " + entry.getKey().getClass());
        visitor.visit((String) entry.getKey(), new PojoJsonView(entry.getValue()));
      }
    } else {
      for (Field field : pojo.getClass().getFields()) {
        if (Modifier.isStatic(field.getModifiers()))
          continue;
        try {
          PojoJsonView fieldView = new PojoJsonView(field.get(pojo), field.getAnnotations());
          visitor.visit(field.getName(), fieldView);
        } catch (IllegalAccessException e) {
          throw new UnsupportedOperationException(e);
        }
      }
    }
  }

  @Override
  public JsonView get(String path) {
    checkIsType(Type.OBJECT);
    if (foundViaGet == null)
      foundViaGet = new HashMap<String, JsonView>();
    JsonView result = foundViaGet.get(path);
    if (result == null) {
      result = doGet(path);
      if (result != null)
        foundViaGet.put(path, result);
    }
    return result;
  }

  private JsonView doGet(String dotSeparatedPath) {
    List<String> path = new ArrayList<String>();
    {
      StringBuilder next = new StringBuilder();
      for (int i = 0; i < dotSeparatedPath.length(); i++) {
        char c = dotSeparatedPath.charAt(i);
        if (c == '.') {
          path.add(next.toString());
          next = new StringBuilder();
        } else {
          next.append(c);
        }
      }
      if (next.length() > 0)
        path.add(next.toString());
    }

    Object result = pojo;

    for (String next : path) {
      if (Map.class.isAssignableFrom(result.getClass())) {
        result = ((Map<?, ?>) result).get(next.toString());
      } else {
        try {
          result = result.getClass().getField(next.toString()).get(result);
        } catch (NoSuchFieldException e) {
          return null;
        } catch (IllegalAccessException e) {
          throw new UnsupportedOperationException(e);
        }
      }
      if (result == null)
        return null;
    }

    return new PojoJsonView(result);
  }

  private void checkIsType(Type t) {
    if (getType() != t)
      throw new UnsupportedOperationException("Unexpected type " + t + ", expected " + getType());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this)
      return true;
    if (o == null || o.getClass() != getClass())
      return false;
    PojoJsonView other = (PojoJsonView) o;
    if (pojo == null)
      return other.pojo == null;
    else
      return pojo.equals(other.pojo);
  }

  @Override
  public int hashCode() {
    return pojo.hashCode();
  }

  @Override
  public String toString() {
    return pojo + "";
  }

  @Override
  public boolean isTransient() {
    return isTransient;
  }

}
