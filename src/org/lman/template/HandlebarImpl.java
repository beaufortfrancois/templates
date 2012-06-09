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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lman.json.JsonConverter;
import org.lman.json.JsonView;
import org.lman.json.JsonView.ArrayVisitor;

/**
 * A "handlebar" template; a mustache-themed template language with a few additional features:
 *   * {{foo.bar}} directly works for object dereferencing.
 *   * The semantics of {{#foo}} has been tightened to just lists, maps, and objects; where {{#foo}}
 *     for an object or map means to promote its members to the current namespace.
 *   * {{?foo}} replaces {{#foo}} as the existential operator (importantly: this now means you can
 *     test to see if a list is non-empty without printing the contents lots of times).
 *   * {{*foo}} renders an object as JSON.
 *
 * This library also supports a few neat things:
 *   * {{+foo}} replaces {{>}} (it's bad with HTML syntax highlighting), and can specifically (in
 *     fact, only; for efficiency purposes) render other {@link HandlebarImpl}s if they're
 *     included as part of the model.
 *   * Supports a switch statement {{:condition}}{{=case1}} {{=case2}} {{/condition}}
 *   * You can use {{@}} to refer to the current item of a list while iterating.
 *   * {@link HandlebarImpl#render} is a varags method allowing the namespace of multiple
 *     objects to be in the top namespace at once.
 *   * There's a handy {{*}} JSON serialisation operator, great for bootstrapping JavaScript.
 *
 * Note that it's written in a bit of a crazy way (i.e. highly "object based" and reflective) to
 * make it easier to port to Javascript/Coffeescript.
 */
// TODO: comments on all platforms
// TODO: a special tag to render errors in with a standard syntax (test for it).
//       make sure that it doesn't have any non-alphanumeric or -_ characters so that it can be
//       safely embedded inside comments (multi-line comments, anyway).
// TODO: blank lines
// TODO: inherit indentation
// TODO: line number errors (above three all related)
// TODO: ? and # only put on @; need to do @.@.@ if you really want to go back?
// TODO: partial inclusion promotes the top-level @ to the top-level namespace.
// TODO: {{|}} to mean "else".
// TODO: only maintain the top-level context in partials, or allow a custom top-level context to
//       be specified.
public final class HandlebarImpl extends Handlebar {

  private interface Identifier {
    String THIS_IDENTIFIER = "@";

    JsonView resolve(Deque<JsonView> contexts, List<String> errors);
  }

  private static class PathIdentifier implements Identifier {
    private final String path;

    public PathIdentifier(String path) {
      if (path.isEmpty())
        throw new ParseException("Cannot have empty identifiers");
      if (!path.matches("^[a-zA-Z0-9._]*$"))
        throw new ParseException(path + " is not a valid identifier");
      this.path = path;
    }

    @Override
    public JsonView resolve(Deque<JsonView> contexts, List<String> errors) {
      JsonView resolved = null;
      for (JsonView context : contexts) {
        // TODO: this is a result of that attempt to make @ a valid path identifier, when
        // really it doesn't make much sense. What would be better is to make @ a special
        // variable defined within array iteration only, then rather than iteration working
        // like {{#foo}} {{x}} {{/}} have {{#foo}} {{@.x}} or {{$.x}} {{/}}.
        if (context.getType() != JsonView.Type.OBJECT)
          continue;

        resolved = context.get(path);
        if (resolved != null)
          return resolved;
      }
      renderError(errors, "Couldn't resolve identifier ", this);
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this)
        return true;
      if (o == null || o.getClass() != this.getClass())
        return false;
      return path.equals(((PathIdentifier) o).path);
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }

    @Override
    public String toString() {
      return path;
    }
  }

  private static class ThisIdentifier implements Identifier {
    public static ThisIdentifier INSTANCE = new ThisIdentifier();

    @Override
    public JsonView resolve(Deque<JsonView> contexts, List<String> errors) {
      return contexts.getFirst();
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof ThisIdentifier;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public String toString() {
      return THIS_IDENTIFIER;
    }
  }

  /** Interface for all objects used as part of rendering templates. */
  private interface Node {
    void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors);
  }

  /** Nodes which are "self closing", e.g. {{foo}}, {{*foo}}. */
  public static abstract class SelfClosingNode implements Node {
    public Identifier id;

    public void init(Identifier id) {
      this.id = id;
    }
  }

  /** Nodes which are not self closing, and have 0..n children. */
  public static abstract class HasChildrenNode implements Node {
    public Identifier id;
    public List<Node> children;

    public void init(Identifier id, List<Node> children) {
      this.id = id;
      this.children = children;
    }
  }

  /** Just a string. */
  public static class StringNode implements Node {
    public final String string;

    public StringNode(String string) {
      this.string = string;
    }

    @Override
    public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      buf.append(string);
    }
  }

  /** {{foo}} */
  public static class EscapedVariableNode extends SelfClosingNode {
    @Override public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      JsonView value = id.resolve(contexts, errors);
      if (value != null && !value.isNull())
        buf.append(htmlEscape(value.toString()));
    }

    private static String htmlEscape(String unescaped) {
      StringBuilder escaped = new StringBuilder();
      for (int i = 0; i < unescaped.length(); i++) {
        char c = unescaped.charAt(i);
        switch (c) {
          case '<': escaped.append("&lt;"); break;
          case '>': escaped.append("&gt;"); break;
          case '&': escaped.append("&amp;"); break;
          default: escaped.append(c);
        }
      }
      return escaped.toString();
    }
  }

  /** {{{foo}}} */
  public static class UnescapedVariableNode extends SelfClosingNode {
    @Override public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      JsonView value = id.resolve(contexts, errors);
      if (value != null && !value.isNull())
        buf.append(value);
    }
  }

  /** {{#foo}} ... {{/}} */
  public static class SectionNode extends HasChildrenNode {
    @Override public void render(
        final StringBuilder buf, final Deque<JsonView> contexts, final List<String> errors) {
      JsonView value = id.resolve(contexts, errors);
      if (value == null)
        return;

      switch (value.getType()) {
        case NULL:
          break;

        case BOOLEAN:
        case NUMBER:
        case STRING:
          renderError(errors, "{{#", id, "}} cannot be rendered with a " + value.getType());
          break;

        case ARRAY:
          value.asArrayForeach(new ArrayVisitor() {
            @Override
            public void visit(JsonView value, int index) {
              contexts.addFirst(value);
              renderNodes(buf, children, contexts, errors);
              contexts.removeFirst();
            }
          });
          break;

        case OBJECT:
          contexts.addFirst(value);
          renderNodes(buf, children, contexts, errors);
          contexts.removeFirst();
          break;
      }
    }
  }

  /** {{?foo}} ... {{/}} */
  public static class VertedSectionNode extends HasChildrenNode {
    @Override public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      // NOTE: null errors because we don't want resolution errors here.
      JsonView value = id.resolve(contexts, null);
      if (value != null && shouldRender(value)) {
        contexts.addFirst(value);
        renderNodes(buf, children, contexts, errors);
        contexts.removeFirst();
      }
    }

    static boolean shouldRender(JsonView value) {
      switch (value.getType()) {
        case NULL:
          return false;
        case BOOLEAN:
          return value.asBoolean();
        case NUMBER:
          return value.asNumber().floatValue() > 0;
        case STRING:
          return value.asString().length() > 0;
        case ARRAY:
          return !value.asArrayIsEmpty();
        case OBJECT:
          return !value.asObjectIsEmpty();
        default:
          throw new UnsupportedOperationException();
      }
    }
  }

  /** {{^foo}} ... {{/}} */
  public static class InvertedSectionNode extends HasChildrenNode {
    @Override public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      // NOTE: null errors because we don't want resolution errors here.
      JsonView value = id.resolve(contexts, null);
      if (value == null || !VertedSectionNode.shouldRender(value))
        renderNodes(buf, children, contexts, errors);
    }
  }

  /** {{*foo}} */
  public static class JsonNode extends SelfClosingNode {
    @Override public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      JsonView value = id.resolve(contexts, errors);
      if (value != null)
        buf.append(JsonConverter.toJson(value));
    }
  }

  /** {{+foo}} */
  public static class PartialNode extends SelfClosingNode {
    @Override public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      JsonView value = id.resolve(contexts, errors);
      Handlebar template = null;
      if (value != null)
        template = value.asInstance(Handlebar.class);
      if (template == null) {
        renderError(errors, id, " didn't resolve to a ", Handlebar.class);
        return;
      }
      template.renderInto(buf, contexts, errors);
    }
  }

  /** {{:foo}} */
  public static class SwitchNode implements Node {
    private final Identifier id;
    private final Map<String, CaseNode> cases = new HashMap<String, CaseNode>();

    public SwitchNode(Identifier id) {
      this.id = id;
    }

    public void addCase(String caseValue, CaseNode caseNode) {
      cases.put(caseValue, caseNode);
    }

    @Override
    public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      JsonView value = id.resolve(contexts, errors);
      if (value == null) {
        renderError(errors, id, " didn't resolve to any value");
        return;
      }
      if (value.getType() != JsonView.Type.STRING) {
        renderError(errors, id, " didn't resolve to a String, instead " + value.getType());
        return;
      }
      CaseNode caseNode = cases.get(value.asString());
      if (caseNode != null)
        caseNode.render(buf, contexts, errors);
    }
  }

  /** {{=foo}} */
  public static class CaseNode implements Node {
    private final List<Node> children;

    public CaseNode(List<Node> children) {
      this.children = children;
    }

    @Override
    public void render(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
      for (Node child : children)
        child.render(buf, contexts, errors);
    }
  }

  /** Tokeniser for template parsing. */
  private static class TokenStream {
    public enum Token {
      // List in order of longest to shortest, to avoid any prefix matching issues.
      OPEN_START_SECTION         ("{{#", SectionNode.class),
      OPEN_START_VERTED_SECTION  ("{{?", VertedSectionNode.class),
      OPEN_START_INVERTED_SECTION("{{^", InvertedSectionNode.class),
      OPEN_START_JSON            ("{{*", JsonNode.class),
      OPEN_START_PARTIAL         ("{{+", PartialNode.class),
      OPEN_START_SWITCH          ("{{:", SwitchNode.class),
      OPEN_CASE                  ("{{=", CaseNode.class),
      OPEN_END_SECTION           ("{{/", null),
      OPEN_UNESCAPED_VARIABLE    ("{{{", UnescapedVariableNode.class),
      CLOSE_MUSTACHE3            ("}}}", null),
      OPEN_COMMENT               ("{{-", null),
      CLOSE_COMMENT              ("-}}", null),
      OPEN_VARIABLE              ("{{" , EscapedVariableNode.class),
      CLOSE_MUSTACHE             ("}}" , null),
      CHARACTER                  ("."  , StringNode.class);

      final String text;
      final Class<?> clazz;

      Token(String text, Class<?> clazz) {
        this.text = text;
        this.clazz = clazz;
      }
    }

    private String remainder;

    public Token nextToken = null;
    public String nextContents = null;

    public TokenStream(String string) {
      this.remainder = string;
      advance();
    }

    public boolean hasNext() {
      return nextToken != null;
    }

    public Token advanceOver(Token token) {
      if (nextToken != token)
        throw new ParseException("Expecting token " + token + " but got " + nextToken);
      return advance();
    }

    public Token advance() {
      nextToken = null;
      nextContents = null;

      if (remainder.isEmpty())
        return null;

      for (Token token : Token.values()) {
        if (remainder.startsWith(token.text)) {
          nextToken = token;
          break;
        }
      }

      if (nextToken == null)
        nextToken = Token.CHARACTER;

      nextContents = remainder.substring(0, nextToken.text.length());
      remainder = remainder.substring(nextToken.text.length());
      return nextToken;
    }

    /** Slight violation of this class' role, but it's too convenient. */
    public String nextString() {
      StringBuilder buf = new StringBuilder();
      while (nextToken == Token.CHARACTER) {
        buf.append(nextContents);
        advance();
      }
      return buf.toString();
    }
  }

  private final List<Node> nodes = new ArrayList<Node>();

  /** Creates a new {@link HandlebarImpl} parsed from a string. */
  public HandlebarImpl(String template) throws ParseException {
    super(template);
    TokenStream tokens = new TokenStream(template);
    parseSection(tokens, nodes);
    if (tokens.hasNext()) {
      throw new ParseException(
          "There are still tokens remaining, was there an end-section without a start-section?");
    }
  }

  private void parseSection(TokenStream tokens, List<Node> nodes) {
    boolean sectionEnded = false;

    while (tokens.hasNext() && !sectionEnded) {
      switch (tokens.nextToken) {
        case CHARACTER:
          nodes.add(new StringNode(tokens.nextString()));
          break;

        case OPEN_VARIABLE:
        case OPEN_UNESCAPED_VARIABLE:
        case OPEN_START_JSON:
        case OPEN_START_PARTIAL: {
          TokenStream.Token token = tokens.nextToken;
          Identifier id = openSection(tokens);

          try {
            SelfClosingNode node = (SelfClosingNode) token.clazz.newInstance();
            node.init(id);
            nodes.add(node);
          } catch (Exception e) {
            e.printStackTrace();
            throw new ParseException(e.toString());
          }
          break;
        }

        case OPEN_START_SECTION:
        case OPEN_START_VERTED_SECTION:
        case OPEN_START_INVERTED_SECTION: {
          TokenStream.Token token = tokens.nextToken;
          Identifier id = openSection(tokens);

          List<Node> children = new ArrayList<Node>();
          parseSection(tokens, children);
          closeSection(tokens, id);

          try {
            HasChildrenNode node = (HasChildrenNode) token.clazz.newInstance();
            node.init(id, children);
            nodes.add(node);
          } catch (Exception e) {
            e.printStackTrace();
            throw new ParseException(e.toString());
          }
          break;
        }

        case OPEN_COMMENT:
          openComment(tokens);
          break;

        case OPEN_START_SWITCH: {
          Identifier id = openSection(tokens);

          // Chew up anything between here and the first case (or the closing of a section, if
          // needs be).
          // TODO: this wouldn't be necessary if we did the blank line optimisation thing.
          while (tokens.nextToken == TokenStream.Token.CHARACTER)
            tokens.advanceOver(TokenStream.Token.CHARACTER);

          SwitchNode switchNode = new SwitchNode(id);
          nodes.add(switchNode);

          while (tokens.hasNext() && tokens.nextToken == TokenStream.Token.OPEN_CASE) {
            tokens.advanceOver(TokenStream.Token.OPEN_CASE);
            String caseValue = tokens.nextString();
            tokens.advanceOver(TokenStream.Token.CLOSE_MUSTACHE);

            List<Node> caseChildren = new ArrayList<Node>();
            // TODO: make parseSection take terminating nodes, pass in OPEN_CASE.
            parseSection(tokens, caseChildren);

            switchNode.addCase(caseValue, new CaseNode(caseChildren));
          }

          closeSection(tokens, id);
          break;
        }

        case OPEN_CASE:
          // See below.
          sectionEnded = true;
          break;

        case OPEN_END_SECTION:
          // Handled after running parseSection within the SECTION cases, so this is a
          // terminating condition. If there *is* an orphaned OPEN_END_SECTION, it will be caught
          // by noticing that there are leftover tokens after termination.
          sectionEnded = true;
          break;

        case CLOSE_MUSTACHE:
          throw new ParseException("Orphaned " + tokens.nextToken);
      }
    }
  }

  private void openComment(TokenStream tokens) {
    tokens.advanceOver(TokenStream.Token.OPEN_COMMENT);
    while (tokens.nextToken != TokenStream.Token.CLOSE_COMMENT) {
      if (tokens.nextToken == TokenStream.Token.OPEN_COMMENT)
        openComment(tokens);
      else
        tokens.advance();
    }
    tokens.advanceOver(TokenStream.Token.CLOSE_COMMENT);
  }

  private Identifier openSection(TokenStream tokens) {
    TokenStream.Token openToken = tokens.nextToken;
    tokens.advance();
    Identifier id = createIdentifier(tokens.nextString());
    if (openToken == TokenStream.Token.OPEN_UNESCAPED_VARIABLE)
      tokens.advanceOver(TokenStream.Token.CLOSE_MUSTACHE3);
    else
      tokens.advanceOver(TokenStream.Token.CLOSE_MUSTACHE);
    return id;
  }

  private void closeSection(TokenStream tokens, Identifier id) {
    tokens.advanceOver(TokenStream.Token.OPEN_END_SECTION);
    String nextString = tokens.nextString();
    if (!nextString.isEmpty() && !id.equals(createIdentifier(nextString))) {
      throw new ParseException(
          "Start section " + id + " doesn't match end section " + nextString);
    }
    tokens.advanceOver(TokenStream.Token.CLOSE_MUSTACHE);
  }

  @Override
  protected void renderInto(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
    renderNodes(buf, nodes, contexts, errors);
  }

  private static void renderNodes(
      StringBuilder buf,
      List<Node> nodes,
      Deque<JsonView> contexts,
      List<String> errors) {
    for (Node node : nodes)
      node.render(buf, contexts, errors);
  }

  private static void renderError(List<String> errors, Object... messages) {
    if (errors == null)
      return;
    StringBuilder buf = new StringBuilder();
    for (Object message : messages)
      buf.append(message);
    errors.add(buf.toString());
  }

  private static Identifier createIdentifier(String path) {
    return Identifier.THIS_IDENTIFIER.equals(path) ?
        ThisIdentifier.INSTANCE : new PathIdentifier(path);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    for (Node node : nodes)
      buf.append(node);
    return buf.toString();
  }

}
