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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.lman.common.Struct;
import org.lman.json.JsonView.ArrayVisitor;
import org.lman.json.JsonView.ObjectVisitor;
import org.lman.json.JsonView.Type;

public class PojoJsonViewTest {

  public static class EmptyObject extends Struct {
  }

  public static class TestObject extends Struct {
    public boolean boolean1 = false;
    public Boolean boolean2 = true;

    public int number1 = 0;
    public Integer number2 = 42;
    public double number3 = 0;
    public Double number4 = 42.42;
    public long number5 = 0;
    public Long number6 = 42L;

    public String string1 = "";
    public String string2 = "hello world";

    public List<TestObject> array1 = new ArrayList<TestObject>();
    public TestObject[] array2 = new TestObject[1];

    public Map<String, TestObject> object1 = new HashMap<String, TestObject>();
    public TestObject object2 = null;
  }

  private TestObject test;

  @Before
  public void setUp() {
    test = new TestObject();
    test.array2[0] = new TestObject();
    test.object1.put("key1", new TestObject());
    test.object1.put("key2", new TestObject());
    test.object1.get("key2").object2 = new TestObject();
    test.object2 = new TestObject();
    test.object2.object2 = new TestObject();
    test.object2.object2.object2 = new TestObject();
  }

  @Test
  public void emptyObject() {
    EmptyObject empty = new EmptyObject();
    JsonView json = new PojoJsonView(empty);

    assertEquals(Type.OBJECT, json.getType());
    assertEquals(empty, json.asInstance(EmptyObject.class));
    assertNull(json.asInstance(TestObject.class));
    assertTrue(json.asObjectIsEmpty());
  }

  @Test
  public void testObject() {
    JsonView json = new PojoJsonView(test);
    assertEquals(Type.OBJECT, json.getType());
    assertEquals(test, json.asInstance(TestObject.class));
    assertNull(json.asInstance(EmptyObject.class));

    final Map<String, JsonView> seen = new HashMap<String, JsonView>();
    json.asObjectForeach(new ObjectVisitor() {
      @Override
      public void visit(String key, JsonView value) {
        assertNull(seen.put(key, value));
      }
    });

    assertEquals(test.boolean1, seen.remove("boolean1").asBoolean());
    assertEquals(test.boolean2, seen.remove("boolean2").asBoolean());
    assertEquals(test.number1, seen.remove("number1").asNumber());
    assertEquals(test.number2, seen.remove("number2").asNumber());
    assertEquals(test.number3, seen.remove("number3").asNumber());
    assertEquals(test.number4, seen.remove("number4").asNumber());
    assertEquals(test.number5, seen.remove("number5").asNumber());
    assertEquals(test.number6, seen.remove("number6").asNumber());
    assertEquals(test.string1, seen.remove("string1").asString());
    assertEquals(test.string2, seen.remove("string2").asString());
    assertEquals(test.array1, asList(seen.remove("array1")));
    // TODO: need tests/bugfixes for Struct, equals() doesn't seem to work?
    //assertArrayEquals(test.array2, asArray(seen.remove("array2")));
    assertEquals(test.array2.length, asArray(seen.remove("array2")).length);
    // TODO: need tests/bugfixes for Struct, equals() doesn't seem to work?
    //assertEquals(test.object1, asMap(seen.remove("object1")));
    assertEquals(test.object1.size(), asMap(seen.remove("object1")).size());
    assertEquals(test.object2, seen.remove("object2").asInstance(TestObject.class));

    assertTrue(seen.isEmpty());
  }

  @Test
  public void get() {
    JsonView json = new PojoJsonView(test);
    assertEquals("", json.get("string1").asString());
    assertEquals("", json.get("object1.key1.string1").asString());
    assertEquals("", json.get("object1.key2.object2.string1").asString());
    assertEquals("", json.get("object2.string1").asString());
    assertEquals("", json.get("object2.object2.string1").asString());
    assertEquals("hello world", json.get("string2").asString());
    assertEquals("hello world", json.get("object1.key1.string2").asString());
    assertEquals("hello world", json.get("object1.key2.object2.string2").asString());
    assertEquals("hello world", json.get("object2.string2").asString());
    assertEquals("hello world", json.get("object2.object2.string2").asString());
    assertNull(json.get("asdasd"));
    assertNull(json.get("asdasd.blah"));
    assertNull(json.get("object1.askjdhjas"));
    assertNull(json.get("object2.sdsddds"));
    assertNull(json.get("object2.object2.dddds"));
  }

  private static List<?> asList(JsonView json) {
    final List<Object> list = new ArrayList<Object>();
    json.asArrayForeach(new ArrayVisitor() {
      @Override
      public void visit(JsonView value, int index) {
        assertEquals(list.size(), index);
        list.add(value);
      }
    });
    return list;
  }

  private static Object[] asArray(JsonView json) {
    return asList(json).toArray();
  }

  private static Map<String, ?> asMap(JsonView json) {
    final Map<String, Object> map = new HashMap<String, Object>();
    json.asObjectForeach(new ObjectVisitor() {
      @Override
      public void visit(String key, JsonView value) {
        map.put(key, value);
      }
    });
    return map;
  }

}
