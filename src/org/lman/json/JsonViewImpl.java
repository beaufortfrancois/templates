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

public class JsonViewImpl implements JsonView {

  @Override
  public Type getType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> E asInstance(Class<E> clazz) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isNull() {
    return false;
  }

  @Override
  public boolean asBoolean() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Number asNumber() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String asString() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean asArrayIsEmpty() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void asArrayForeach(ArrayVisitor visitor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean asObjectIsEmpty() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void asObjectForeach(ObjectVisitor visitor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JsonView get(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isTransient() {
    return false;
  }

}
