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

import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A JSON view of JSON text.
 *
 * @author kalman
 *
 */
public class JSONObjectJsonView extends JsonViewImpl {

  private static class JSONArrayJsonView extends JsonViewImpl {
    private final JSONArray array;

    private JSONArrayJsonView(JSONArray array) {
      this.array = array;
    }

    @Override
    public Type getType() {
      return Type.ARRAY;
    }

    @Override
    public boolean asArrayIsEmpty() {
      return array.length() == 0;
    }

    @Override
    public void asArrayForeach(ArrayVisitor visitor) {
      for (int i = 0, length = array.length(); i < length; i++) {
        Object item = array.opt(i);
        if (item instanceof JSONObject)
          visitor.visit(new JSONObjectJsonView((JSONObject) item), i);
        else if (item instanceof JSONArray)
          visitor.visit(new JSONArrayJsonView((JSONArray) item), i);
        else if (item == JSONObject.NULL)
          visitor.visit(new PojoJsonView(null), i);
        else
          visitor.visit(new PojoJsonView(item), i);
      }
    }
  }

  private final JSONObject json;

  public JSONObjectJsonView(JSONObject json) {
    this.json = json;
  }

  @Override
  public Type getType() {
    return Type.OBJECT;
  }

  @Override
  public boolean asObjectIsEmpty() {
    return !json.keys().hasNext();
  }

  @Override
  public void asObjectForeach(ObjectVisitor visitor) {
    Iterator<?> keys = json.keys();
    while (keys.hasNext()) {
      String key = (String) keys.next();
      visitor.visit(key, get(key));
    }
  }

  @Override
  public JsonView get(String path) {
    String[] split = path.split("\\.");

    Object item = json;
    for (int i = 0; i < split.length && item != null; i++) {
      if (item instanceof JSONObject)
        item = ((JSONObject) item).opt(split[i]);
      else
        item = null;
    }

    if (item == null)
      return null;
    else if (item instanceof JSONObject)
      return new JSONObjectJsonView((JSONObject) item);
    else if (item instanceof JSONArray)
      return new JSONArrayJsonView((JSONArray) item);
    else if (item == JSONObject.NULL)
      return new PojoJsonView(null);
    else
      return new PojoJsonView(item);
  }

}
