package org.lman.common;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.lman.json.JsonConverter;

public class StructTest {

  // TODO: test maps, objects.
  protected static class Simple extends Struct {
    public Boolean aBoolean;
    public Integer aNumber;
    public String aString;
    @TypeParameter(String.class) public List<String> aList;

    public Simple(
        Boolean aBoolean,
        Integer aNumber,
        String aString,
        List<String> aList) {
      this.aBoolean = aBoolean;
      this.aNumber = aNumber;
      this.aString = aString;
      this.aList = aList;
    }

    public Simple() {}
  }

  @Test
  public void simple() {
    Simple reference = new Simple(
        true,
        42,
        "hello",
        asList("one", "two"));
    Simple test = JsonConverter.copy(reference);

    assertEquals(reference, test);
    assertEquals(reference.hashCode(), test.hashCode());

    test.aBoolean = false;

    assertFalse(reference.equals(test));
    assertFalse(reference.hashCode() == test.hashCode());

    test.aBoolean = reference.aBoolean;
    test.aNumber = 7;

    assertFalse(reference.equals(test));
    assertFalse(reference.hashCode() == test.hashCode());

    test.aNumber = reference.aNumber;
    test.aString = "goodbye";

    assertFalse(reference.equals(test));
    assertFalse(reference.hashCode() == test.hashCode());

    test.aString = reference.aString;
    test.aList = asList();

    assertFalse(reference.equals(test));
    assertFalse(reference.hashCode() == test.hashCode());

    test.aList = asList("four");

    assertFalse(reference.equals(test));
    assertFalse(reference.hashCode() == test.hashCode());

    test.aList = asList("four", "five", "six");

    assertFalse(reference.equals(test));
    assertFalse(reference.hashCode() == test.hashCode());

    test.aList = asList("four", "five", "six", "seven", "eight");

    assertFalse(reference.equals(test));
    assertFalse(reference.hashCode() == test.hashCode());

    test.aList = new ArrayList<String>(reference.aList);

    assertEquals(reference, test);
    assertEquals(reference.hashCode(), test.hashCode());
  }

  protected static class HasTransient extends Struct {
    public Integer aInteger;
    @Transient Integer aTransient;

    public HasTransient(Integer aInteger, Integer aTransient) {
      this.aInteger = aInteger;
      this.aTransient = aTransient;
    }
  }

  @Test
  public void transient_() {
    assertEquals(
        new HasTransient(1, 1),
        new HasTransient(1, 1));
    assertEquals(
        new HasTransient(1, 1).hashCode(),
        new HasTransient(1, 1).hashCode());
    assertEquals(
        new HasTransient(1, 1),
        new HasTransient(1, 2));
    assertEquals(
        new HasTransient(1, 1).hashCode(),
        new HasTransient(1, 2).hashCode());
  }
}
