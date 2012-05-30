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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.lman.common.TypeParameter;
import org.lman.common.Struct;

public class JsonConverterTest {

  protected static class KeyToStringValuesTest extends Struct {
    public String foo;
    public String bar;

    KeyToStringValuesTest() {}
    KeyToStringValuesTest(String foo, String bar) {
      this.foo = foo;
      this.bar = bar;
    }
  }

  @Test
  public void toJsonKeyToStringValues() throws JSONException {
    KeyToStringValuesTest test = new KeyToStringValuesTest("hello", "world");
    JSONObject actual = new JSONObject(JsonConverter.toJson(test));
    JSONObject expected = new JSONObject()
        .put("foo", "hello")
        .put("bar", "world");
    assertEquals(expected, actual);
  }

  @Test
  public void fromJsonKeyToStringValues() throws JSONException {
    JSONObject test = new JSONObject()
        .put("foo", "hello")
        .put("bar", "world");
    KeyToStringValuesTest actual =
        JsonConverter.fromJson(test.toString(), KeyToStringValuesTest.class);
    KeyToStringValuesTest expected = new KeyToStringValuesTest("hello", "world");
    assertEquals(expected, actual);
  }

  protected static class KeyToStringListTest extends Struct {
    @TypeParameter(String.class)
    public List<String> items;

    KeyToStringListTest() {}
    KeyToStringListTest(String... strings) {
      this.items = new ArrayList<String>(Arrays.asList(strings));
    }
  }

  @Test
  public void toJsonKeyToStringListTest() throws JSONException {
    KeyToStringListTest test = new KeyToStringListTest("one", "two", "three");
    JSONObject actual = new JSONObject(JsonConverter.toJson(test));
    JSONObject expected = new JSONObject()
        .put("items", new JSONArray()
            .put("one")
            .put("two")
            .put("three"));
    assertEquals(expected, actual);
  }

  @Test
  public void fromJsonKeyToStringListTest() throws JSONException  {
    JSONObject test = new JSONObject()
        .put("items", new JSONArray()
            .put("one")
            .put("two")
            .put("three"));
    KeyToStringListTest actual =
        JsonConverter.fromJson(test.toString(), KeyToStringListTest.class);
    KeyToStringListTest expected = new KeyToStringListTest("one", "two", "three");
    assertEquals(expected, actual);
  }

  protected static class KeysToObjectsTest extends Struct {
    protected static class Inner1 extends Struct {
      public String foo;

      public Inner1() {}
      public Inner1(String foo) {
        this.foo = foo;
      }
    }

    protected static class Inner2 extends Struct {
      public String bar;

      public Inner2() {}
      public Inner2(String bar) {
        this.bar = bar;
      }
    }

    public Inner1 i1;
    public Inner2 i2;

    public KeysToObjectsTest() {}
    public KeysToObjectsTest(String i1, String i2) {
      this.i1 = new Inner1(i1);
      this.i2 = new Inner2(i2);
    }
  }

  @Test
  public void toJsonKeysToObjectsTest() throws JSONException {
    KeysToObjectsTest test = new KeysToObjectsTest("hello", "world");
    JSONObject actual = new JSONObject(JsonConverter.toJson(test));
    JSONObject expected = new JSONObject()
        .put("i1", new JSONObject().put("foo", "hello"))
        .put("i2", new JSONObject().put("bar", "world"));
    assertEquals(expected, actual);
  }

  @Test
  public void fromJsonKeysToObjectsTest() throws JSONException {
    JSONObject test = new JSONObject()
        .put("i1", new JSONObject().put("foo", "hello"))
        .put("i2", new JSONObject().put("bar", "world"));
    KeysToObjectsTest actual = JsonConverter.fromJson(test.toString(), KeysToObjectsTest.class);
    KeysToObjectsTest expected = new KeysToObjectsTest("hello", "world");
    assertEquals(expected, actual);
  }

  protected static class KeysToObjectListTest extends Struct {
    protected static class Inner1 extends Struct {
      public String foo;

      public Inner1() {}
      public Inner1(String foo) {
        this.foo = foo;
      }
    }

    protected static class Inner2 extends Struct {
      @TypeParameter(Inner1.class)
      public List<Inner1> i1s;

      Inner2() {}
      Inner2(List<String> strings) {
        i1s = new ArrayList<Inner1>();
        for (String s : strings)
          i1s.add(new Inner1(s));
      }
    }

    @TypeParameter(Inner2.class)
    public List<Inner2> i2s;

    public KeysToObjectListTest() {}
    public KeysToObjectListTest(List<List<String>> lists) {
      i2s = new ArrayList<Inner2>();
      for (List<String> stringList : lists)
        i2s.add(new Inner2(stringList));
    }
  }

  @Test
  public void toJsonKeysToObjectListTest() throws JSONException {
    @SuppressWarnings("unchecked")
    KeysToObjectListTest test = new KeysToObjectListTest(Arrays.asList(
        Arrays.asList("one", "two", "three"),
        Arrays.asList("hello", "world")));
    JSONObject actual = new JSONObject(JsonConverter.toJson(test));
    JSONObject expected = new JSONObject()
        .put("i2s", new JSONArray()
          .put(new JSONObject().put("i1s", new JSONArray()
              .put(new JSONObject().put("foo", "one"))
              .put(new JSONObject().put("foo", "two"))
              .put(new JSONObject().put("foo", "three"))))
          .put(new JSONObject().put("i1s", new JSONArray()
              .put(new JSONObject().put("foo", "hello"))
              .put(new JSONObject().put("foo", "world")))));
    assertEquals(expected, actual);
  }

  @Test
  public void fromJsonKeysToObjectListTest() throws JSONException {
    JSONObject test = new JSONObject()
        .put("i2s", new JSONArray()
          .put(new JSONObject().put("i1s", new JSONArray()
              .put(new JSONObject().put("foo", "one"))
              .put(new JSONObject().put("foo", "two"))
              .put(new JSONObject().put("foo", "three"))))
          .put(new JSONObject().put("i1s", new JSONArray()
              .put(new JSONObject().put("foo", "hello"))
              .put(new JSONObject().put("foo", "world")))));
    KeysToObjectListTest actual =
        JsonConverter.fromJson(test.toString(), KeysToObjectListTest.class);
    @SuppressWarnings("unchecked")
    KeysToObjectListTest expected = new KeysToObjectListTest(Arrays.asList(
        Arrays.asList("one", "two", "three"),
        Arrays.asList("hello", "world")));
    assertEquals(expected, actual);
  }

  protected static class InheritanceTest extends Struct {
    protected static abstract class Inner1 extends Struct {
      public String i1;

      public Inner1() {}
      public Inner1(String i1) {
        this.i1 = i1;
      }
    }

    protected static class Inner2 extends Inner1 {
      public String i2;

      public Inner2() {}
      public Inner2(String i1, String i2) {
        super(i1);
        this.i2 = i2;
      }
    }

    public Inner2 inner;

    public InheritanceTest() {}
    public InheritanceTest(String i1, String i2) {
      this.inner = new Inner2(i1, i2);
    }
  }

  @Test
  public void toJsonInheritance() throws JSONException {
    InheritanceTest test = new InheritanceTest("foo", "bar");
    JSONObject actual = new JSONObject(JsonConverter.toJson(test));
    JSONObject expected = new JSONObject()
        .put("inner", new JSONObject()
          .put("i1", "foo")
          .put("i2", "bar"));
    assertEquals(expected, actual);
  }

  @Test
  public void fromJsonInheritance() throws JSONException {
    JSONObject test = new JSONObject()
        .put("inner", new JSONObject()
          .put("i1", "foo")
          .put("i2", "bar"));
    InheritanceTest actual = JsonConverter.fromJson(test.toString(), InheritanceTest.class);
    InheritanceTest expected = new InheritanceTest("foo", "bar");
    assertEquals(expected, actual);
  }

  protected static class Primitives extends Struct {
    public Integer x;
    public Boolean finished;
    public Integer length; // TODO Long

    public Primitives() {}
    public Primitives(int x, boolean finished, long length) {
      this.x = x;
      this.finished = finished;
      this.length = (int) length;
    }
  }

  @Test
  public void toPrimitives() throws JSONException {
    Primitives test = new Primitives(42, true, 0);
    JSONObject actual = new JSONObject(JsonConverter.toJson(test));
    JSONObject expected = new JSONObject()
        .put("x", new Integer(42))
        .put("finished", Boolean.TRUE)
        .put("length", new Integer(0));
    assertEquals(actual, expected);
  }

  @Test
  public void fromPrimitives() throws JSONException {
    JSONObject test = new JSONObject()
        .put("x", new Integer(42))
        .put("finished", Boolean.TRUE)
        .put("length", new Integer(0));
    Primitives actual = JsonConverter.fromJson(test.toString(), Primitives.class);
    Primitives expected = new Primitives(42, true, 0);
    assertEquals(actual, expected);
  }

  protected static class EnumTest extends Struct {
    public enum Unit {
      CM,
      IN
    }

    public Unit unit;
    public Integer value;

    public EnumTest() {}
    public EnumTest(Unit unit, Integer value) {
      this.unit = unit;
      this.value = value;
    }
  }

  @Test
  public void toEnums() throws JSONException {
    EnumTest test = new EnumTest(EnumTest.Unit.CM, 10);
    JSONObject actual = new JSONObject(JsonConverter.toJson(test));
    JSONObject expected = new JSONObject()
        .put("unit", "CM")
        .put("value", 10);
    assertEquals(actual, expected);
  }

  @Test
  public void fromEnums() throws JSONException {
    JSONObject test = new JSONObject()
        .put("unit", "in")
        .put("value", 20);
    EnumTest actual = JsonConverter.fromJson(test.toString(), EnumTest.class);
    EnumTest expected = new EnumTest(EnumTest.Unit.IN, 20);
    assertEquals(actual, expected);
  }

  protected static class Empty extends Struct {
  }

  @Test
  public void toEmpty() {
    Empty test = new Empty();
    assertEquals("{}", JsonConverter.toJson(test));
  }

  @Test
  public void fromEmpty() {
    assertEquals(new Empty(), JsonConverter.fromJson("{}", Empty.class));
  }

  protected static class HasTransientField extends Struct {
    public String field1;

    //@Transient
    public String field2;

    public HasTransientField(String field1, String field2) {
      this.field1 = field1;
      this.field2 = field2;
    }
}

  public void transientField() throws JSONException {
    HasTransientField test = new HasTransientField("foo", "bar");
    JSONObject actual = new JSONObject(JsonConverter.toJson(test));
    JSONObject expected = new JSONObject().put("field1", "foo");
    assertEquals(expected, actual);
  }

  // TODO: randomised symmetric tests (performance checking)
}
