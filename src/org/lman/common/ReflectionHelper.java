package org.lman.common;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflection utilities.
 */
public class ReflectionHelper {

  private ReflectionHelper() {}

  public static abstract class FieldVisitor {
    protected enum Control { CONTINUE, BREAK }

    private Object returnValue = null;

    protected abstract Control visit(Field field, Object value);

    protected Control breakAndReturn(Object value) {
      returnValue = value;
      return Control.BREAK;
    }
  }

  public static Object forEach(Object obj, FieldVisitor visitor) {
    for (Field field : obj.getClass().getFields()) {
      if (Modifier.isStatic(field.getModifiers()))
        continue;
      if (field.getAnnotation(Transient.class) != null)
        continue;

      Object fieldValue = null;
      try {
        fieldValue = field.get(obj);
      } catch (IllegalAccessException e) {
        throw new IllegalArgumentException(e);
      }

      switch (visitor.visit(field, fieldValue)) {
        case CONTINUE:
          continue;
        case BREAK:
          break;
      }
    }

    return visitor.returnValue;
  }

  public static Object getOrNull(Object obj, String fieldName) {
    try {
      return obj.getClass().getField(fieldName).get(obj);
    } catch (SecurityException e) {
      return null;
    } catch (IllegalAccessException e) {
      return null;
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public static Object copy(Object object) {
    if (object == null)
      return null;

    Class<?> clazz = object.getClass();
    if (Boolean.class.isAssignableFrom(clazz)
        || Number.class.isAssignableFrom(clazz)
        || String.class.isAssignableFrom(clazz)) {
      // Immutable, no copying needed.
      return object;
    }

    if (Collection.class.isAssignableFrom(clazz)) {
      Collection asCollection = (Collection) object;
      Collection copy = null;
      if (List.class.isAssignableFrom(clazz))
        copy = new ArrayList(asCollection.size());
      else if (Set.class.isAssignableFrom(clazz))
        copy = new HashSet(asCollection.size());
      else
        throw new IllegalArgumentException("Unsupported Collection type " + clazz);
      for (Object obj : asCollection)
        copy.add(copy(obj));
      return copy;
    }

    if (Map.class.isAssignableFrom(clazz)) {
      Map asMap = (Map) object;
      Map copy = new HashMap(asMap.size());
      for (Map.Entry entry : (Set<Map.Entry>) asMap.entrySet())
        copy.put(entry.getKey(), copy(entry.getValue()));
      return copy;
    }

    try {
      final Object copy = object.getClass().getConstructor().newInstance();
      forEach(object, new FieldVisitor() {
        @Override
        protected Control visit(Field field, Object value) {
          try {
            field.set(copy, copy(value));
          } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
          }
          return Control.CONTINUE;
        }
      });
      return copy;
    } catch (SecurityException e) {
      throw new IllegalArgumentException(e);
    } catch (InstantiationException e) {
      throw new IllegalArgumentException(e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException(e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(e);
    }
  }

}
