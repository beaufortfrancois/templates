package org.lman.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.lman.common.Union.IllegalUnionException;

class Singleton extends Union {
  public String foo = null;

  public Singleton() {
  }

  public Singleton(String foo) {
    this.foo = foo;
  }
}

class Multiple extends Union {
  public String foo = null;
  public Integer bar = null;
  public Boolean baz = null;

  public Multiple() {
  }

  public Multiple(String foo) {
    this.foo = foo;;
  }

  public Multiple(Integer bar) {
    this.bar = bar;
  }

  public Multiple(Boolean baz) {
    this.baz = baz;
  }

  public Multiple(String foo, Integer bar, Boolean baz) {
    this.foo = foo;
    this.bar = bar;
    this.baz = baz;
  }
}

public class UnionTest {

  @Test
  public void singleton() {
    assertEquals("foo".hashCode(), new Singleton("foo").hashCode());

    assertEquals(new Singleton("foo"), new Singleton("foo"));

    assertNotEquals(new Singleton(""), new Singleton("foo"));
    assertNotEquals(new Singleton("foo"), new Singleton(""));
    assertNotEquals(new Singleton("fooo"), new Singleton("foo"));
    assertNotEquals(new Singleton("foo"), new Singleton("fooo"));

    assertEquals("foo", new Singleton("foo").toString());
  }

  @Test
  public void multiple() {
    assertEquals("foo".hashCode(), new Multiple("foo").hashCode());
    assertEquals(Integer.valueOf(42).hashCode(), new Multiple(42).hashCode());
    assertEquals(Boolean.TRUE.hashCode(), new Multiple(true).hashCode());

    assertEquals(new Multiple("foo"), new Multiple("foo"));
    assertEquals(new Multiple(42), new Multiple(42));
    assertEquals(new Multiple(false), new Multiple(false));

    assertNotEquals(new Multiple("foo"), new Multiple(42));
    assertNotEquals(new Multiple("foo"), new Multiple(true));
    assertNotEquals(new Multiple(42), new Multiple("foo"));
    assertNotEquals(new Multiple(42), new Multiple(true));
    assertNotEquals(new Multiple(true), new Multiple("foo"));
    assertNotEquals(new Multiple(false), new Multiple(42));

    assertEquals("foo", new Multiple("foo").toString());
    assertEquals("42", new Multiple(42).toString());
    assertEquals("true", new Multiple(true).toString());
  }

  @Test
  public void illegalSingleton() {
    try {
      new Singleton().hashCode();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Singleton().toString();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Singleton().equals(new Singleton("foo"));
      fail();
    } catch (IllegalUnionException pass) {}
  }

  @Test
  public void illegalMultiple() {
    try {
      new Multiple().hashCode();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple().toString();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple().equals(new Multiple("foo"));
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", 42, null).hashCode();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", 42, null).toString();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", 42, null).equals(new Multiple("foo"));
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", null, true).hashCode();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", null, true).toString();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", null, true).equals(new Multiple("foo"));
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple(null, 42, false).hashCode();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple(null, 42, false).toString();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple(null, 42, false).equals(new Multiple("foo"));
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", 42, false).hashCode();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", 42, false).toString();
      fail();
    } catch (IllegalUnionException pass) {}

    try {
      new Multiple("foo", 42, false).equals(new Multiple("foo"));
      fail();
    } catch (IllegalUnionException pass) {}
  }

  private static void assertNotEquals(Object a, Object b) {
    assertFalse(a.equals(b));
  }

}
