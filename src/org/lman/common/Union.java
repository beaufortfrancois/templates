package org.lman.common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * A data type where exactly one of the members should be non-{@code null}.
 */
// TODO: needs to handle arrays.
public abstract class Union {

  public class IllegalUnionException extends IllegalStateException {
    public IllegalUnionException(String message) {
      super(message);
    }
  }

  @Override
  public final boolean equals(Object other) {
    Boolean result = null;

    for (Field f : getClass().getFields()) {
      if (Modifier.isStatic(f.getModifiers()))
        continue;
      if (f.getAnnotation(Transient.class) != null)
        continue;

      Object thisValue;
      try {
        thisValue = f.get(this);
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }

      if (thisValue == null)
        continue;
      else if (result != null)
        throw new IllegalUnionException("Multiple fields have a non-null value.");

      Object otherValue = null;
      try {
        otherValue = f.get(other);
      } catch (IllegalArgumentException e) {
        // Other was not a compatible class.
        // Is there another way to check this?
        result = false;
      } catch (IllegalAccessException e) {
        throw new AssertionError();
      }

      // For validation, don't early-exit.
      result = thisValue.equals(otherValue);
    }

    if (result == null)
      throw new IllegalUnionException("No fields have a value.");
    return result;
  }

  @Override
  public final int hashCode() {
    Integer hashCode = null;

    for (Field f : getClass().getFields()) {
      if (Modifier.isStatic(f.getModifiers()))
        continue;
      if (f.getAnnotation(Transient.class) != null)
        continue;

      Object value;
      try {
        value = f.get(this);
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }

      if (value == null)
        continue;
      else if (hashCode != null)
        throw new IllegalUnionException("Multiple fields have a non-null value.");
      else
        hashCode = value.hashCode();  // For validation, don't early-exit.
    }

    if (hashCode == null)
      throw new IllegalUnionException("No fields have a value.");
    return hashCode;
  }

  @Override
  public final String toString() {
    String string = null;

    for (Field f : getClass().getFields()) {
      if (Modifier.isStatic(f.getModifiers()))
        continue;
      if (f.getAnnotation(Transient.class) != null)
        continue;

      Object value;
      try {
        value = f.get(this);
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }

      if (value == null)
        continue;
      else if (string != null)
        throw new IllegalUnionException("Multiple fields have a non-null value.");
      else
        string = value.toString();  // For validation, don't early-exit.
    }

    if (string == null)
      throw new IllegalUnionException("No fields have a value.");
    return string;
  }

}
