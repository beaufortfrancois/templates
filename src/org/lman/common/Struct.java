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

package org.lman.common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * {@link Object} wrapper that treats them as "structs", that is, generates equals/hashCode/toString
 * based on reflection over the public methods.
 */
// TODO: unify reflection stuff using JsonView?
public abstract class Struct {

  @Override
  public final boolean equals(Object o) {
    try {
      return equals2(o);
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private boolean equals2(final Object other)
      throws IllegalArgumentException, IllegalAccessException {
    if (this == other)
      return true;
    if (other == null)
      return false;
    if (getClass() != other.getClass())
      return false;

    Boolean result = (Boolean) ReflectionHelper.forEach(this, new ReflectionHelper.FieldVisitor() {
      @Override
      public Control visit(Field field, Object value) {
        Object otherValue = null;
        try {
          otherValue = field.get(other);
        } catch (IllegalAccessException e) {
          return breakAndReturn(false);
        }
        if (value == null) {
          if (otherValue != null)
            return breakAndReturn(false);
        } else {
          if (field.getType().isArray()) {
            if (!Arrays.deepEquals((Object[]) value, (Object[]) otherValue))
              return breakAndReturn(false);
          } else {
            if (!value.equals(otherValue))
              return breakAndReturn(false);
          }
        }
        return Control.CONTINUE;
      }
    });

    return (result == null) ? true : result.booleanValue();
  }

  @Override
  public final int hashCode() {
    try {
      return hashCode2();
    } catch (Exception e) {
      e.printStackTrace();
      return 0;
    }
  }

  private int hashCode2() throws IllegalArgumentException, IllegalAccessException {
    int prime = 31, result = 1;
    for (Field f : getClass().getFields()) {
      if (Modifier.isStatic(f.getModifiers()))
        continue;
      if (f.getAnnotation(Transient.class) != null)
        continue;

      Object thisField = f.get(this);
      if (thisField != null) {
        result *= prime;
        if (f.getType().isArray())
          result += Arrays.deepHashCode((Object[]) thisField);
        else
          result += (prime * result) + thisField.hashCode();
      }
    }
    return result;
  }

  @Override
  public final String toString() {
    try {
      return toString2();
    } catch (Exception e) {
      return getClass().getName() + "{ ??? }";
    }
  }

  private String toString2() throws IllegalArgumentException, IllegalAccessException {
    StringBuilder buf = new StringBuilder(getClass().getSimpleName() + "{ ");
    boolean needsComma = false;

    for (Field f : getClass().getFields()) {
      if (Modifier.isStatic(f.getModifiers()))
        continue;

      if (needsComma)
        buf.append(", ");
      else
        needsComma = true;
      buf.append(f.getName() + ": ");

      Object thisField = f.get(this);
      if (thisField == null) {
        buf.append("(null)");
      } else {
        if (f.getType().isArray())
          buf.append(Arrays.deepToString((Object[]) thisField));
        else
          buf.append(thisField.toString());
      }
    }

    buf.append(" }");
    return buf.toString();
  }
}
