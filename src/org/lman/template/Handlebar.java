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

package org.lman.template;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.lman.common.Struct;
import org.lman.json.JsonConverter;
import org.lman.json.JsonView;
import org.lman.json.PojoJsonView;

/**
 * A {@link Handlebar} is an abstract class rather than an interface so that it can expose
 * {@link Handlebar#source} for serialization purposes (i.e. from {@link JsonConverter}).
 */
public abstract class Handlebar {

  /**
   * Return value from {@link Handlebar#render}.
   */
  public static class RenderResult extends Struct {
    public final String text;
    public final List<String> errors;

    public RenderResult(String text, List<String> errors) {
      this.text = text;
      this.errors = errors;
    }
  }

  /**
   * Thrown if parsing {@link Handlebar#source} fails.
   */
  public static class ParseException extends RuntimeException {
    public ParseException(String error) {
      super(error);
    }
  }

  /** Source text of the template. */
  public String source;

  protected Handlebar(String source) {
    this.source = source;
  }

  /**
   * Renders the template given some number of objects to take variables from.
   */
  public RenderResult render(Object... contexts) {
    StringBuilder buf = new StringBuilder();
    List<String> errors = new ArrayList<String>();
    Deque<JsonView> contextList = new LinkedList<JsonView>();
    for (Object context : contexts) {
      if (context instanceof JsonView)
        contextList.add((JsonView) context);
      else
        contextList.add(new PojoJsonView(context));
    }
    renderInto(buf, contextList, errors);
    return new RenderResult(buf.toString(), errors);
  }

  /**
   * Renders the template into a buffer, given a stack of objects to take variables from, and
   * a list of errors to populate if necessary.
   */
  protected abstract void renderInto(
      StringBuilder buf, Deque<JsonView> contexts, List<String> errors);

}
