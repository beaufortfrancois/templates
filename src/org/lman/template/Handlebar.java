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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lman.common.Struct;
import org.lman.json.JsonConverter;
import org.lman.json.JsonView;
import org.lman.json.JsonView.ArrayVisitor;
import org.lman.json.PojoJsonView;

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
 *   * You can use {{@}} to refer to the current item of a list while iterating.
 *   * {@link HandlebarImpl#render} is a varags method allowing the namespace of multiple
 *     objects to be in the top namespace at once.
 *   * There's a handy {{*}} JSON serialisation operator, great for bootstrapping JavaScript.
 *
 * Note that it's written in a bit of a crazy way (i.e. highly "object based" and reflective) to
 * make it easier to port to Javascript/Coffeescript.
 */
// TODO: comments on all platforms.
// TODO: {{|}} or {{-}} to mean "else".
// TODO: experiment with ensureCapacity when calling Node#render.
public class Handlebar {

  /**
   * Thrown if parsing {@link Handlebar#source} fails.
   */
  public static class ParseException extends RuntimeException {
    public ParseException(String error, Line line) {
      // TODO: add template filename here too.
      super(error + " (line " + line.number + ")");
    }
  }

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
   * The state of a {@link #render} call.
   */
  private static class RenderState {
    public final Deque<JsonView> globalContexts;
    public final Deque<JsonView> localContexts;
    public final StringBuilder text = new StringBuilder();
    public final List<String> errors = new ArrayList<String>();

    private boolean errorsDisabled = false;

    public RenderState(Deque<JsonView> globalContexts, Deque<JsonView> localContexts) {
      this.globalContexts = globalContexts;
      this.localContexts = localContexts;
    }

    public RenderState inSameContext() {
      return new RenderState(globalContexts, localContexts);
    }

    public JsonView getFirstContext() {
      return localContexts.isEmpty() ? globalContexts.getFirst() : localContexts.getFirst();
    }

    public RenderState disableErrors() {
      errorsDisabled = true;
      return this;
    }

    public RenderState addError(Object... messages) {
      if (errorsDisabled)
        return this;
      StringBuilder buf = new StringBuilder();
      for (Object message : messages)
        buf.append(message);
      errors.add(buf.toString());
      return this;
    }

    public RenderResult getResult() {
      return new RenderResult(text.toString(), errors);
    }
  }

  /**
   * An identifier of the form either just '@' to refer to the head of the context, or
   * foo.bar.baz, with an optional '@.' in the front.
   */
  private static class Identifier {
    private final boolean isThis;
    private final boolean startsWithThis;
    private final String path;

    public Identifier(String path, Line line) {
      isThis = path.equals("@");
      if (isThis) {
        this.startsWithThis = false;
        this.path = "";
        return;
      }

      String thisDot = "@.";
      startsWithThis = path.startsWith(thisDot);
      if (startsWithThis)
          path = path.substring(thisDot.length());

      if (!path.matches("^[a-zA-Z0-9._]+$"))
        throw new ParseException("'" + path + "' is not a valid identifier", line);
      this.path = path;
    }

    public JsonView resolve(RenderState renderState) {
      if (isThis)
        return renderState.getFirstContext();

      if (startsWithThis)
        return resolveFromContext(renderState.getFirstContext());

      JsonView resolved = resolveFromContexts(renderState.localContexts);
      if (resolved == null)
        resolved = resolveFromContexts(renderState.globalContexts);
      if (resolved == null)
        renderState.addError("Couldn't resolve identifier ", this);
      return resolved;
    }

    private JsonView resolveFromContexts(Deque<JsonView> contexts) {
      for (JsonView context : contexts) {
        JsonView resolved = resolveFromContext(context);
        if (resolved != null)
          return resolved;
      }
      return null;
    }

    private JsonView resolveFromContext(JsonView context) {
      if (context.getType() != JsonView.Type.OBJECT)
        return null;
      return context.get(path);
    }

    @Override
    public boolean equals(Object o) {
      if (o == this)
        return true;
      if (!(o instanceof Identifier))
        return false;
      Identifier other = (Identifier) o;
      return path.equals(other.path) &&
             isThis == other.isThis &&
             startsWithThis == other.startsWithThis;
    }

    @Override
    public int hashCode() {
      return path.hashCode() +
             Boolean.valueOf(isThis).hashCode() +
             Boolean.valueOf(startsWithThis).hashCode();
    }

    @Override
    public String toString() {
      return isThis ? "@" : ((startsWithThis ? "@." : "") + path);
    }
  }

  /**
   * A line within the original template.
   */
  private static class Line {
    public final int number;

    public Line(int number) {
      this.number = number;
    }
  }

  /**
   * A node within the parsed content of the template.
   */
  private interface Node {
    void render(RenderState renderState);
    void trimLeadingNewLine();
    int trimTrailingSpaces();
    void trimTrailingNewLine();
    boolean trailsWithEmptyLine();
  }

  /**
   * Generic implementation of a node that is a "leaf" of the template tree and isn't delegating any
   * functionality to another node.
   */
  private static abstract class LeafNode implements Node {
    @Override
    public void trimLeadingNewLine() {
    }

    @Override
    public int trimTrailingSpaces() {
      return 0;
    }

    @Override
    public void trimTrailingNewLine() {
    }

    @Override
    public boolean trailsWithEmptyLine() {
      return false;
    }
  }

  /**
   * Generic implementation of a node which decorates some other node.
   */
  private static abstract class DecoratorNode implements Node {
    protected final Node content;

    protected DecoratorNode(Node content) {
      this.content = content;
    }

    @Override
    public void trimLeadingNewLine() {
      content.trimLeadingNewLine();
    }

    @Override
    public int trimTrailingSpaces() {
      return content.trimTrailingSpaces();
    }

    @Override
    public void trimTrailingNewLine() {
      content.trimTrailingNewLine();
    }

    @Override
    public boolean trailsWithEmptyLine() {
      return content.trailsWithEmptyLine();
    }
  }

  /**
   * A node that is rendered inline. Newline characters are removed. For example:
   *   hello {{inlineNode}} world.
   *   hello {{#inlineNode}}hi{{/}} world.
   */
  private static class InlineNode extends DecoratorNode {
    public InlineNode(Node content) {
      super(content);
    }

    @Override
    public void render(RenderState renderState) {
      RenderState contentRenderState = renderState.inSameContext();
      content.render(contentRenderState);

      renderState.errors.addAll(contentRenderState.errors);

      for (int i = 0; i < contentRenderState.text.length(); i++) {
        char c = contentRenderState.text.charAt(i);
        if (c != '\n')
          renderState.text.append(c);
      }
    }
  }

  /**
   * A node that is rendered as an indented set of lines. If the wrapped node doesn't render with
   * any content, no indentation nor line breaks are rendered either. For example:
   *   {{+indentedNode}}
   *   {{?indentedNode}}something{{/}}
   */
  private static class IndentedNode extends DecoratorNode {
    private final int indentation;

    public IndentedNode(Node content, int indentation) {
      super(content);
      this.indentation = indentation;
    }

    @Override
    public void render(RenderState renderState) {
      RenderState contentRenderState = renderState.inSameContext();
      content.render(contentRenderState);

      renderState.errors.addAll(contentRenderState.errors);

      indent(renderState.text);
      for (int i = 0; i < contentRenderState.text.length(); i++) {
        char c = contentRenderState.text.charAt(i);
        renderState.text.append(c);
        if (c == '\n' && i < renderState.text.length() - 1)
          indent(renderState.text);
      }
      renderState.text.append('\n');
    }

    private void indent(StringBuilder buf) {
      for (int i = 0; i < indentation; i++)
        buf.append(' ');
    }
  }

  /**
   * A node that is rendered as a block. For example:
   * {{#foo}}
   *   hello
   * {{/}}
   */
  private static class BlockNode extends DecoratorNode {
    public BlockNode(Node content) {
      super(content);
      content.trimLeadingNewLine();
      content.trimTrailingSpaces();
    }

    @Override
    public void render(RenderState renderState) {
      content.render(renderState);
    }
  }

  /**
   * Exposes a collection of nodes as a single node.
   */
  private static class NodeCollection implements Node {
    private final Node[] nodes;

    public NodeCollection(List<Node> nodes) {
      this.nodes = new Node[nodes.size()];
      nodes.toArray(this.nodes);
    }

    @Override
    public void render(RenderState renderState) {
      for (Node node: nodes)
        node.render(renderState);
    }

    @Override
    public void trimLeadingNewLine() {
      if (nodes.length > 0)
        nodes[0].trimLeadingNewLine();
    }

    @Override
    public int trimTrailingSpaces() {
      return (nodes.length > 0) ? nodes[nodes.length - 1].trimTrailingSpaces() : 0;
    }

    @Override
    public void trimTrailingNewLine() {
      if (nodes.length > 0)
        nodes[nodes.length - 1].trimTrailingNewLine();
    }

    @Override
    public boolean trailsWithEmptyLine() {
      return nodes.length > 0 ? nodes[nodes.length - 1].trailsWithEmptyLine() : false;
    }
  }

  /**
   * A node containing a string (may have \n etc). The basic building block of the templates.
   */
  private static class StringNode implements Node {
    private String string;

    public StringNode(String string) {
      this.string = string;
    }

    @Override
    public void render(RenderState renderState) {
      renderState.text.append(string);
    }

    @Override
    public void trimLeadingNewLine() {
      if (string.startsWith("\n"))
        string = string.substring(1);
    }

    @Override
    public int trimTrailingSpaces() {
      int originalLength = string.length();
      string = string.substring(0, lastIndexOfSpaces());
      return originalLength - string.length();
    }

    @Override
    public void trimTrailingNewLine() {
      if (string.endsWith("\n"))
        string = string.substring(0, string.length() - 1);
    }

    @Override
    public boolean trailsWithEmptyLine() {
      int index = lastIndexOfSpaces();
      return index == 0 || string.charAt(index - 1) == '\n';
    }

    private int lastIndexOfSpaces() {
      int index = string.length();
      while (index > 0 && string.charAt(index - 1) == ' ')
        index--;
      return index;
    }
  }

  /**
   * {{foo}}
   */
  private static class EscapedVariableNode extends LeafNode {
    private final Identifier id;

    @SuppressWarnings("unused")
    public EscapedVariableNode(Identifier id) {
      this.id = id;
    }

    @Override
    public void render(RenderState renderState) {
      JsonView value = id.resolve(renderState);
      if (value != null && !value.isNull())
        appendEscapedHtml(renderState.text, value.toString());
    }

    private static void appendEscapedHtml(StringBuilder escaped, String unescaped) {
      for (int i = 0; i < unescaped.length(); i++) {
        char c = unescaped.charAt(i);
        switch (c) {
          case '<': escaped.append("&lt;"); break;
          case '>': escaped.append("&gt;"); break;
          case '&': escaped.append("&amp;"); break;
          default: escaped.append(c);
        }
      }
    }
  }

  /**
   * {{{foo}}}
   */
  private static class UnescapedVariableNode extends LeafNode {
    private final Identifier id;

    @SuppressWarnings("unused")
    public UnescapedVariableNode(Identifier id) {
      this.id = id;
    }

    @Override
    public void render(RenderState renderState) {
      JsonView value = id.resolve(renderState);
      if (value != null && !value.isNull())
        renderState.text.append(value);
    }
  }

  /**
   * {{#foo}} {{/}}
   */
  private static class SectionNode extends DecoratorNode {
    private final Identifier id;

    public SectionNode(Identifier id, Node content) {
      super(content);
      this.id = id;
    }

    @Override
    public void render(final RenderState renderState) {
      JsonView value = id.resolve(renderState);
      if (value == null)
        return;

      switch (value.getType()) {
        case NULL:
          break;

        case BOOLEAN:
        case NUMBER:
        case STRING:
          renderState.addError("{{#", id, "}} cannot be rendered with a ", value.getType());
          break;

        case ARRAY:
          value.asArrayForeach(new ArrayVisitor() {
            @Override
            public void visit(JsonView value, int index) {
              renderState.localContexts.addFirst(value);
              content.render(renderState);
              renderState.localContexts.removeFirst();
            }
          });
          break;

        case OBJECT:
          renderState.localContexts.addFirst(value);
          content.render(renderState);
          renderState.localContexts.removeFirst();
          break;
      }
    }
  }

  /**
   * {{?foo}} {{/}}
   */
  private static class VertedSectionNode extends DecoratorNode {
    private final Identifier id;

    public VertedSectionNode(Identifier id, Node content) {
      super(content);
      this.id = id;
    }

    @Override
    public void render(RenderState renderState) {
      JsonView value = id.resolve(renderState.inSameContext().disableErrors());
      if (value != null && shouldRender(value)) {
        renderState.localContexts.addFirst(value);
        content.render(renderState);
        renderState.localContexts.removeFirst();
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

  /**
   * {{^foo}} {{/}}
   */
  private static class InvertedSectionNode extends DecoratorNode {
    private final Identifier id;

    public InvertedSectionNode(Identifier id, Node content) {
      super(content);
      this.id = id;
    }

    @Override
    public void render(RenderState renderState) {
      JsonView value = id.resolve(renderState.inSameContext().disableErrors());
      if (value == null || !VertedSectionNode.shouldRender(value))
        content.render(renderState);
    }
  }

  /**
   * {{*foo}}
   */
  private static class JsonNode extends LeafNode {
    private final Identifier id;

    @SuppressWarnings("unused")
    public JsonNode(Identifier id) {
      this.id = id;
    }

    @Override
    public void render(RenderState renderState) {
      JsonView value = id.resolve(renderState);
      if (value != null)
        renderState.text.append(JsonConverter.toJson(value));
    }
  }

  /**
   * {{+foo}}
   */
  private static class PartialNode extends LeafNode {
    private final Identifier id;
    private Map<String, Identifier> args = null;

    public PartialNode(Identifier id) {
      this.id = id;
    }

    @Override
    public void render(RenderState renderState) {
      JsonView value = id.resolve(renderState);
      Handlebar template = null;
      if (value != null)
        template = value.asInstance(Handlebar.class);
      if (template == null) {
        renderState.addError(id, " didn't resolve to a ", Handlebar.class);
        return;
      }

      ArrayDeque<JsonView> argContext = new ArrayDeque<JsonView>();
      if (!renderState.localContexts.isEmpty())
        argContext.addFirst(renderState.localContexts.getFirst());

      if (args != null) {
        Map<String, JsonView> argContextMap = new HashMap<String, JsonView>();
        for (Map.Entry<String, Identifier> entry : args.entrySet()) {
          JsonView context = entry.getValue().resolve(renderState);
          if (context != null)
            argContextMap.put(entry.getKey(), context);
        }
        argContext.addLast(new PojoJsonView(argContextMap));
      }

      RenderState partialRenderState = new RenderState(renderState.globalContexts, argContext);
      template.topNode.render(partialRenderState);

      // Partials are special; we don't want to render the trailing \n because it looks ugly
      // (typically editors will add a \n at the end of documents).
      // If partials want a trailing \n they need to add it explicitly.
      int lastIndex = partialRenderState.text.length() - 1;
      if (lastIndex >= 0 && partialRenderState.text.charAt(lastIndex) == '\n')
        partialRenderState.text.deleteCharAt(lastIndex);

      renderState.text.append(partialRenderState.text);
      renderState.errors.addAll(partialRenderState.errors);
    }

    public void addArgument(String key, Identifier valueId) {
      if (args == null)
        args = new HashMap<String, Identifier>();
      args.put(key, valueId);
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
      OPEN_END_SECTION           ("{{/", null),
      OPEN_UNESCAPED_VARIABLE    ("{{{", UnescapedVariableNode.class),
      CLOSE_MUSTACHE3            ("}}}", null),
      OPEN_COMMENT               ("{{-", null),
      CLOSE_COMMENT              ("-}}", null),
      OPEN_VARIABLE              ("{{" , EscapedVariableNode.class),
      CLOSE_MUSTACHE             ("}}" , null),
      CHARACTER                  ("."  , null);

      final String text;
      final Class<? extends Node> clazz;

      Token(String text, Class<? extends Node> clazz) {
        this.text = text;
        this.clazz = clazz;
      }
    }

    private String remainder;

    public Token nextToken = null;
    public String nextContents = null;
    public Line nextLine = null;

    public TokenStream(String string) {
      this.remainder = string;
      nextLine = new Line(1);
      advance();
    }

    /**
     * Gets whether there are any more tokens in the stream.
     */
    public boolean hasNext() {
      return nextToken != null;
    }

    /**
     * Advances the stream by 1 token, setting nextToken/nextContents/nextLine as needed.
     */
    public TokenStream advance() {
      if ("\n".equals(nextContents))
        nextLine = new Line(nextLine.number + 1);

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
      return this;
    }

    /**
     * Like {@link #advance} but asserts that the next token is the one given.
     */
    public TokenStream advanceOver(Token token) {
      if (nextToken != token)
        throw new ParseException("Expecting token " + token + " but got " + nextToken, nextLine);
      return advance();
    }

    /**
     * Advances the token stream over the next continuous stream of characters, while those
     * characters are not in an excluded set, and returns the string formed by those characters.
     */
    public String advanceOverNextString(String excluded) {
      StringBuilder buf = new StringBuilder();
      while (nextToken == Token.CHARACTER && excluded.indexOf(nextContents.charAt(0)) == -1) {
        buf.append(nextContents);
        advance();
      }
      return buf.toString();
    }

    public String advanceOverNextString() {
      return advanceOverNextString("");
    }
  }

  /** Source of the template. Public to be included when serialized to JSON. */
  public final String source;

  /** Top-level node. */
  private final Node topNode;

  /**
   * Creates a new {@link HandlebarImpl} parsed from a string.
   */
  public Handlebar(String template) throws ParseException {
    this.source = template;
    TokenStream tokens = new TokenStream(template);
    this.topNode = parseSection(tokens, null);
    if (tokens.hasNext()) {
      throw new ParseException(
          "There are still tokens remaining, was there an end-section without a start-section?",
          tokens.nextLine);
    }
  }

  private Node parseSection(TokenStream tokens, Node previousNode) {
    List<Node> nodes = new ArrayList<Node>();
    boolean sectionEnded = false;

    while (tokens.hasNext() && !sectionEnded) {
      TokenStream.Token token = tokens.nextToken;
      Node node = null;

      switch (token) {
        case CHARACTER:
          node = new StringNode(tokens.advanceOverNextString());
          break;

        case OPEN_VARIABLE:
        case OPEN_UNESCAPED_VARIABLE:
        case OPEN_START_JSON: {
          Identifier id = openSectionOrTag(tokens);
          try {
            node = token.clazz.getConstructor(Identifier.class).newInstance(id);
          } catch (Exception e) {
            throw new AssertionError(e);
          }
          break;
        }

        case OPEN_START_PARTIAL: {
          // Hand-write partial code because it's special.
          tokens.advance();
          Identifier id = new Identifier(tokens.advanceOverNextString(" "), tokens.nextLine);
          PartialNode partialNode = new PartialNode(id);

          // Parse the arguments to the partial.
          while (tokens.nextToken == TokenStream.Token.CHARACTER) {
            tokens.advance();
            String key = tokens.advanceOverNextString(":");
            tokens.advance();  // past ':'
            partialNode.addArgument(
                key,
                new Identifier(tokens.advanceOverNextString(" "), tokens.nextLine));
          }

          tokens.advanceOver(TokenStream.Token.CLOSE_MUSTACHE);
          node = partialNode;
          break;
        }

        case OPEN_START_SECTION:
        case OPEN_START_VERTED_SECTION:
        case OPEN_START_INVERTED_SECTION: {
          Line startLine = tokens.nextLine;

          Identifier id = openSectionOrTag(tokens);
          Node section = parseSection(tokens, previousNode);
          closeSection(tokens, id);

          try {
            node = token.clazz.getConstructor(Identifier.class, Node.class)
                .newInstance(id, section);
          } catch (Exception e) {
            throw new AssertionError(e);
          }

          if (startLine != tokens.nextLine) {
            node = new BlockNode(node);
            if (previousNode != null)
              previousNode.trimTrailingSpaces();
            if ("\n".equals(tokens.nextContents))
              tokens.advance();
          }
          break;
        }

        case OPEN_COMMENT:
          advanceOverComment(tokens);
          break;

        case OPEN_END_SECTION:
          // Handled after running parseSection within the SECTION cases, so this is a
          // terminating condition. If there *is* an orphaned OPEN_END_SECTION, it will be caught
          // by noticing that there are leftover tokens after termination.
          sectionEnded = true;
          break;

        case CLOSE_MUSTACHE:
          throw new ParseException("Orphaned " + tokens.nextToken, tokens.nextLine);
      }

      if (node == null)
        continue;

      // If it's a non-string node (and not already made into a block), determine whether it's
      // inline vs the only node on the line.
      if (!(node instanceof StringNode) && !(node instanceof BlockNode)) {
        if ((previousNode == null || previousNode.trailsWithEmptyLine()) &&
            (!tokens.hasNext() || tokens.nextContents.equals("\n"))) {
          int indentation = 0;
          if (previousNode != null)
            indentation = previousNode.trimTrailingSpaces();
          tokens.advance(); // over \n
          node = new IndentedNode(node, indentation);
        } else {
          node = new InlineNode(node);
        }
      }

      previousNode = node;
      nodes.add(node);
    }

    return (nodes.size() == 1) ? nodes.get(0) : new NodeCollection(nodes);
  }

  private void advanceOverComment(TokenStream tokens) {
    tokens.advanceOver(TokenStream.Token.OPEN_COMMENT);
    int depth = 1;
    while (tokens.hasNext() && depth > 0) {
      switch (tokens.nextToken) {
        case OPEN_COMMENT:
          depth++;
          break;
        case CLOSE_COMMENT:
          depth--;
          break;
      }
      tokens.advance();
    }
  }

  private Identifier openSectionOrTag(TokenStream tokens) {
    TokenStream.Token openToken = tokens.nextToken;
    tokens.advance();
    Identifier id = new Identifier(tokens.advanceOverNextString(), tokens.nextLine);
    tokens.advanceOver(openToken == TokenStream.Token.OPEN_UNESCAPED_VARIABLE ?
        TokenStream.Token.CLOSE_MUSTACHE3 : TokenStream.Token.CLOSE_MUSTACHE);
    return id;
  }

  private void closeSection(TokenStream tokens, Identifier id) {
    tokens.advanceOver(TokenStream.Token.OPEN_END_SECTION);
    String nextString = tokens.advanceOverNextString();
    if (!nextString.isEmpty() && !nextString.equals(id.toString())) {
      throw new ParseException(
          "Start section " + id + " doesn't match end section " + nextString, tokens.nextLine);
    }
    tokens.advanceOver(TokenStream.Token.CLOSE_MUSTACHE);
  }

  /**
   * Renders the template given some number of objects to take variables from.
   */
  public RenderResult render(Object... contexts) {
    Deque<JsonView> globalContexts = new ArrayDeque<JsonView>();
    for (Object context : contexts) {
      if (context instanceof JsonView)
        globalContexts.addLast((JsonView) context);
      else
        globalContexts.addLast(new PojoJsonView(context));
    }
    RenderState renderState = new RenderState(globalContexts, new ArrayDeque<JsonView>());
    topNode.render(renderState);
    return renderState.getResult();
  }
}
