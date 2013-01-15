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
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.lman.common.TypeParameter;
import org.lman.json.JsonView.ArrayVisitor;
import org.lman.json.JsonView.ObjectVisitor;

public class JsonConverter {

  private static class ReadJsonException extends Exception {
    private static final long serialVersionUID = 1L;

    public ReadJsonException(String message) {
      super(message);
    }
  }

  private JsonConverter() {}

  @SuppressWarnings("unchecked")
  public static <E> E fromJson(String json, Class<E> clazz) {
    try {
      return (E) readJson(new JSONObject(json), clazz, null);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static Object readJson(Object json, Class<?> clazz, TypeParameter typeParameter)
      throws ReadJsonException,
      InstantiationException,
      IllegalAccessException,
      JSONException,
      IllegalArgumentException,
      SecurityException,
      InvocationTargetException,
      NoSuchMethodException {
    if (json == null || json == JSONObject.NULL) {
      return null;
    } else if (
        clazz == String.class ||
        clazz == Boolean.class ||
        clazz == Integer.class) {
      return json;
    } else if (clazz == Long.class) {
      // It's a Number but not an Integer, need to convert it into an integer for JSON :(
      return Long.valueOf(((Number) json).longValue());
    } else if (Collection.class.isAssignableFrom(clazz)) {
      return readCollection(json, clazz, typeParameter);
    } else if (clazz.isEnum()) {
      return readEnum(json, clazz);
    } else if (Map.class.isAssignableFrom(clazz)) {
      if (typeParameter == null)
        throw new ReadJsonException("Missing type parameter");
      Map<String, Object> map = new HashMap<String, Object>();
      JSONObject jsonObject = (JSONObject) json;
      for (String key : jsonObject.keySet())
        map.put(key, readJson(jsonObject.get(key), typeParameter.value(), null));
      return map;
    } else {
      if (!(json instanceof JSONObject))
        throw new ReadJsonException(json + " not a JSONObject (is " + json.getClass() +
            " for clazz " + clazz + ")");
      Object object = clazz.newInstance();
      for (Field field : clazz.getFields()) {
        if (Modifier.isStatic(field.getModifiers()))
          continue;

        try {
          Object readObject = readJson(
              ((JSONObject) json).get(field.getName()),
              field.getType(),
              field.getAnnotation(TypeParameter.class));
          field.set(object, readObject);
        } catch (JSONException e) {
          // Ok, probably just not found.
        }
      }
      return object;
    }
  }

  private static Enum<?> readEnum(Object json, Class<?> clazz)
      throws ReadJsonException {
    if (!(json instanceof String))
      throw new ReadJsonException(json + " not a String, cannot Enumify");
    for (Object asObject : clazz.getEnumConstants()) {
      Enum<?> asEnum = (Enum<?>) asObject;
      if (asEnum.name().equalsIgnoreCase((String) json))
        return asEnum;
    }
    throw new ReadJsonException(clazz + " has no matching enum for " + json);
  }

  private static Collection<?> readCollection(
      Object json,
      Class<?> clazz,
      TypeParameter typeAnnotation)
      throws ReadJsonException,
      InstantiationException,
      IllegalAccessException,
      JSONException,
      InvocationTargetException,
      NoSuchMethodException {
    if (!(json instanceof JSONArray))
      throw new ReadJsonException(json + " not a JSONArray");
    if (typeAnnotation == null)
      throw new ReadJsonException(json + " has no Type annotation");
    Collection<Object> collection = null;
    if (List.class.isAssignableFrom(clazz))
      collection = new ArrayList<Object>();
    else if (TreeSet.class.isAssignableFrom(clazz))
      collection = new TreeSet<Object>();
    else if (Set.class.isAssignableFrom(clazz))
      collection = new HashSet<Object>();
    for (int i = 0, length = ((JSONArray) json).length(); i < length; i++) {
      collection.add(readJson(((JSONArray) json).get(i), typeAnnotation.value(), null));
    }
    return collection;
  }

  public static String toJson(JsonView json) {
    try {
      JSONStringer jsonStringer = new JSONStringer();
      writeJson(json, jsonStringer);
      return jsonStringer.toString();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static String toJson(Object object) {
    return toJson(new PojoJsonView(object));
  }

  private static void writeJson(JsonView json, final JSONStringer out)
       throws IllegalAccessException, JSONException {
    switch (json.getType()) {
      case NULL:
        out.value(null);
        break;

      case BOOLEAN:
        out.value(json.asBoolean());
        break;

      case NUMBER:
        out.value(json.asNumber());
        break;

      case STRING:
        out.value(json.asString());
        break;

      case ARRAY:
        out.array();
        json.asArrayForeach(new ArrayVisitor() {
          @Override
          public void visit(JsonView value, int index) {
            if (value.isNull())
              return;

            try {
              writeJson(value, out);
            } catch (IllegalAccessException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            } catch (JSONException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
        });
        out.endArray();
        break;

      case OBJECT:
        out.object();
        try {
          json.asObjectForeach(new ObjectVisitor() {
            @Override
            public void visit(String key, JsonView value) {
              if (value.isTransient() || value.isNull())
                return;

              try {
                out.key(key);
                writeJson(value, out);
              } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              } catch (IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }
          });
        } catch (Exception e) {
        }
        out.endObject();
        break;
    }
  }

  /**
   * Copies an object via JSON conversion and back.
   */
  @SuppressWarnings("unchecked")
  public static <E> E copy(E object) {
    return (E) fromJson(toJson(object), object.getClass());
  }
}
