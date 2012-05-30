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


/**
 * A read-only view over something as JSON.
 *
 * @author kalman
 *
 */
public interface JsonView {

  interface ArrayVisitor {
    void visit(JsonView value, int index);
  }

  interface ObjectVisitor {
    void visit(String key, JsonView value);
  }

  enum Type {
    NULL,
    BOOLEAN,
    NUMBER,
    STRING,
    ARRAY,
    OBJECT
  }

  Type getType();

  /**
   * If the underlying object is of class |clazz|, returns the object as that class. Otherwise
   * returns null.
   */
  <E> E asInstance(Class<E> clazz);
  
  /** 
   * Whether this object should be skipped during serialization. 
   */
  boolean isTransient();

  // Cast operations to non-collections.
  boolean isNull();
  boolean asBoolean();
  Number asNumber();
  String asString();

  // Operations over collections.
  boolean asArrayIsEmpty();
  void asArrayForeach(ArrayVisitor visitor);
  boolean asObjectIsEmpty();
  void asObjectForeach(ObjectVisitor visitor);

  // TODO: separate get() vs getPath().
  JsonView get(String path);
}
