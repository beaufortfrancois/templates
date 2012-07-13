# Copyright 2012 Benjamin Kalman
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

startsWith = (string, prefix) ->
  return string.slice(0, prefix.length) == prefix

endsWith = (string, suffix) ->
  return string.slice(string.length - suffix.length) == suffix

extend = (array, extendWith) ->
  for item in extendWith
    array.push(item)

class ParseException extends Error
  constructor: (error, line) ->
    super(error + " (line " + line + ")")

class RenderResult
  constructor: (@text, @errors) ->

  appendTo: (element) ->
    tempElement = document.createElement(element.tagName)
    tempElement.innerHTML = @text
    while tempElement.childNodes.length > 0
      element.appendChild(tempElement.firstChild)

  insertBefore: (element) ->
    parent = element.parentElement
    tempElement = document.createElement(parent.tagName)
    tempElement.innerHTML = @text
    while tempElement.childNodes.length > 0
      parent.insertBefore(tempElement.firstChild, element)

class StringBuilder
  constructor: () ->
    @_buffer = []
    @length = 0

  append: (s) ->
    string = s.toString()
    @_buffer.push(string)
    @length += string.length

  toString: () ->
    return @_buffer.join('')

class RenderState
  constructor: (@globalContexts, @localContexts) ->
    @text = new StringBuilder()
    @errors = []
    @_errorsDisabled = false

  inSameContext: () ->
    return new RenderState(@globalContexts, @localContexts)

  getFirstContext: () ->
    if @localContexts.length > 0
      return @localContexts[0]
    if @globalContexts.length > 0
      return @globalContexts[0]
    return null

  disableErrors: () ->
    @_errorsDisabled = true
    return this

  addError: (messages...) ->
    if @_errorsDisabled
      return this
    buf = new StringBuilder()
    for message in messages
      buf.append(message)
    @errors.push(buf.toString())
    return this

  getResult: () ->
    return new RenderResult(@text.toString(), @errors)

  toString: () ->
    listToString = (list) ->
      if (list.length == 0)
        return "[]"
      buf = new StringBuilder()
      buf.append("[")
      is_first = true
      for e in list
        if not is_first
          buf.append(",")
          is_first = false
        buf.append(JSON.stringify(e))
      buf.append("]")
      return buf.toString()

    return "RenderState {\"" +
           "  text: " + @text + "\n" +
           "  errors: " + listToString(@errors) + "\n" +
           "  _errorsDisabled: " + @_errorsDisabled + "\n" +
           "  localContext: " + listToString(@localContexts) + "\n" +
           "  globalContext: " + listToString(@globalContexts) + "}"

class Identifier
  constructor: (name, line) ->
    @_isThis = (name == '@')
    if @_isThis
      @_startsWithThis = false
      @_path = []
      return

    thisDot = '@.'
    @_startsWithThis = startsWith(name, thisDot)
    if @_startsWithThis
      name = name.slice(thisDot.length)

    if not /^[a-zA-Z0-9._]*$/.test(name)
      throw new ParseException(name + " is not a valid identifier", line)
    @_path = name.split(".")

  resolve: (renderState) ->
    if @_isThis
      return renderState.getFirstContext()

    if @_startsWithThis
      return @_resolveFromContext(renderState.getFirstContext())

    resolved = @_resolveFromContexts(renderState.localContexts)
    if not resolved?
      resolved = @_resolveFromContexts(renderState.globalContexts)
    if not resolved?
      renderState.addError("Couldn't resolve identifier ", @_path)
    return resolved

  _resolveFromContexts: (contexts) ->
    for context in contexts
      resolved = @_resolveFromContext(context)
      if resolved?
        return resolved
    return null

  _resolveFromContext: (context) ->
    result = context
    for next in @_path
      if not result? or typeof(result) != 'object'
        return null
      result = result[next]
    return result

  toString: () ->
    if @_isThis
      return '@'
    name = @_path.join('.')
    return if @_startsWithThis then ('@.' + name) else name

class Line
  constructor: (@number) ->

class LeafNode
  constructor: (line) ->
    @_line = line

  startsWithNewLine: () ->
    return false

  trimStartingNewLine: () ->

  trimEndingSpaces: () ->
    return 0

  trimEndingNewLine: () ->

  endsWithEmptyLine: () ->
    return false

  getStartLine: () ->
    return @_line

  getEndLine: () ->
    return @_line

class DecoratorNode
  constructor: (content) ->
    @_content = content

  startsWithNewLine: () ->
    return @_content.startsWithNewLine()

  trimStartingNewLine: () ->
    @_content.trimStartingNewLine()

  trimEndingSpaces: () ->
    return @_content.trimEndingSpaces()

  trimEndingNewLine: () ->
    @_content.trimEndingNewLine()

  endsWithEmptyLine: () ->
    return @_content.endsWithEmptyLine()

  getStartLine: () ->
    return @_content.getStartLine()

  getEndLine: () ->
    return @_content.getEndLine()

class InlineNode extends DecoratorNode
  contructor: (content) ->
    super(content)

  render: (renderState) ->
    contentRenderState = renderState.inSameContext()
    @_content.render(contentRenderState)

    extend(renderState.errors, contentRenderState.errors)

    for c in contentRenderState.text.toString()
      if c != '\n'
        renderState.text.append(c)

  toString: () ->
    return "INLINE(" + @_content + ")"

class IndentedNode extends DecoratorNode
  constructor: (content, indentation) ->
    super(content)
    @_indentation = indentation

  render: (renderState) ->
    contentRenderState = renderState.inSameContext()
    @_content.render(contentRenderState)

    extend(renderState.errors, contentRenderState.errors)

    @_indent(renderState.text)
    for c, i in contentRenderState.text.toString()
      renderState.text.append(c)
      if c == '\n' and i < contentRenderState.text.length - 1
        @_indent(renderState.text)
    renderState.text.append('\n')

  _indent: (buf) ->
    iterations = @_indentation
    while iterations-- > 0
      buf.append(' ')

  toString: () ->
    return "INDENTED(" + @_indentation + "," + @_content + ")"

class BlockNode extends DecoratorNode
  constructor: (content) ->
    super(content)
    content.trimStartingNewLine()
    content.trimEndingSpaces()

  render: (renderState) ->
    @_content.render(renderState)

  toString: () ->
    return "BLOCK(" + @_content + ")"

class NodeCollection
  constructor: (nodes) ->
    if nodes.length == 0
      throw new Error()
    @_nodes = nodes

  render: (renderState) ->
    for node in @_nodes
      node.render(renderState)

  startsWithNewLine: () ->
    return @_nodes[0].startsWithNewLine()

  trimStartingNewLine: () ->
    @_nodes[0].trimStartingNewLine()

  trimEndingSpaces: () ->
    return @_nodes[@_nodes.length - 1].trimEndingSpaces()

  trimEndingNewLine: () ->
    @_nodes[@_nodes.length - 1].trimEndingNewLine()

  endsWithNewLine: () ->
    return @_nodes[@_nodes.length - 1].endsWithEmptyLine()

  getStartLine: () ->
    return @_nodes[0].getStartLine()

  getEndLine: () ->
    return @_nodes[@_nodes.length - 1].getEndLine()

  toString: () ->
    buf = new StringBuilder()
    for node in @_nodes
      buf.append(node)
    return buf.toString()

class StringNode
  constructor: (string, startLine, endLine) ->
    @_string = string
    @_startLine = startLine
    @_endLine = endLine

  render: (renderState) ->
    renderState.text.append(@_string)

  startsWithNewLine: () ->
    return startsWith(@_string, '\n')

  trimStartingNewLine: () ->
    if @startsWithNewLine()
      @_string = @_string.slice(1)

  trimEndingSpaces: () ->
    originalLength = @_string.length
    @_string = @_string.slice(0, @_lastIndexOfSpaces())
    return originalLength - @_string.length

  trimEndingNewLine: () ->
    if endsWith(@_string, '\n')
      @_string = @_string.slice(0, @_string.length - 1)

  endsWithEmptyLine: () ->
    index = @_lastIndexOfSpaces()
    return index == 0 or @_string[index - 1] == '\n'

  _lastIndexOfSpaces: () ->
    index = @_string.length
    while index > 0 and @_string[index - 1] == ' '
      index--
    return index

  getStartLine: () ->
    return @_startLine

  getEndLine: () ->
    return @_endLine

  toString: () ->
    return "STRING(" + @_string + ")"

class EscapedVariableNode extends LeafNode
  constructor: (id, line) ->
    super(line)
    @_id = id

  render: (renderState) ->
    value = @_id.resolve(renderState)
    if value?
      @_appendEscapedHtml(renderState.text, value.toString())

  _appendEscapedHtml: (escaped, unescaped) ->
    for c in unescaped
      switch c
        when '<' then escaped.append("&lt;")
        when '>' then escaped.append("&gt;")
        when '&' then escaped.append("&amp;")
        else escaped.append(c)

  toString: () ->
    return "{{" + @_id + "}}"

class UnescapedVariableNode extends LeafNode
  constructor: (id, line) ->
    super(line)
    @_id = id

  render: (renderState) ->
    value = @_id.resolve(renderState)
    if value?
      renderState.text.append(value)

  toString: () ->
    return "{{{" + @_id + "}}}"

class SectionNode extends DecoratorNode
  constructor: (id, content) ->
    super(content)
    @_id = id

  render: (renderState) ->
    value = @_id.resolve(renderState)
    if not value?
      return

    if value instanceof Array
      for item in value
        renderState.localContexts.unshift(item)
        @_content.render(renderState)
        renderState.localContexts.shift()
    else if typeof(value) == 'object'
      renderState.localContexts.unshift(value)
      @_content.render(renderState)
      renderState.localContexts.shift()
    else
      renderState.addError("{{#", @_id, "}} cannot be rendered with that type")

  toString: () ->
    return "{{#" + @_id + "}}" + @_content + "{{/" + @_id + "}}"

class VertedSectionNode extends DecoratorNode
  constructor: (id, content) ->
    super(content)
    @_id = id

  render: (renderState) ->
    value = @_id.resolve(renderState.inSameContext().disableErrors())
    if VertedSectionNode.shouldRender(value)
      renderState.localContexts.unshift(value)
      @_content.render(renderState)
      renderState.localContexts.shift()

  toString: () ->
    return "{{?" + @_id + "}}" + @_content + "{{/" + @_id + "}}"

VertedSectionNode.shouldRender = (value) ->
  if not value?
    return false
  type = typeof value
  if type == 'boolean'
    return value
  if type == 'number'
    return true
  if type == 'string'
    return true
  if value instanceof Array
    return value.length > 0
  if type == 'object'
    return true
  throw new Error("Unhandled type: " + type)

class InvertedSectionNode extends DecoratorNode
  constructor: (id, content) ->
    super(content)
    @_id = id

  render: (renderState) ->
    value = @_id.resolve(renderState.inSameContext().disableErrors())
    if not VertedSectionNode.shouldRender(value)
      @_content.render(renderState)

  toString: () ->
    return "{{^" + @_id + "}}" + @_content + "{{/" + @_id + "}}"

class JsonNode extends LeafNode
  constructor: (id, line) ->
    super(line)
    @_id = id

  render: (renderState) ->
    value = @_id.resolve(renderState)
    if value?
      renderState.text.append(JSON.stringify(value))

  toString: () ->
    return "{{*" + @_id + "}}"

class PartialNode extends LeafNode
  constructor: (id, line) ->
    super(line)
    @_id = id
    @_args = null

  render: (renderState) ->
    value = @_id.resolve(renderState)
    if not (value instanceof Handlebar)
      renderState.addError(@_id, " didn't resolve to a Handlebar")
      return

    argContext = []
    if renderState.localContexts.length > 0
      argContext.push(renderState.localContexts[0])

    if @_args?
      argContextMap = {}
      for own key, valueId of @_args
        context = valueId.resolve(renderState)
        if context?
          argContextMap[key] = context
      argContext.push(argContextMap)

    partialRenderState = new RenderState(renderState.globalContexts, argContext)
    value._topNode.render(partialRenderState)

    text = partialRenderState.text.toString()
    if text.length > 0 and text[text.length - 1] == '\n'
      text = text.slice(0, text.length - 1)

    renderState.text.append(text)
    extend(renderState.errors, partialRenderState.errors)

  addArgument: (key, valueId) ->
    if not @_args?
      @_args = {}
    @_args[key] = valueId

  toString: () ->
    return "{{+" + @_id + "}}"

class Token
  constructor: (@name, @text, @clazz) ->

  elseNodeClass: () ->
    if @clazz == VertedSectionNode
      return InvertedSectionNode
    if @clazz == InvertedSectionNode
      return VertedSectionNode
    throw new Error(@name + " can not have an else clause.")

# List in order of longest to shortest, to avoid any prefix matching issues.
Token.values = [
  new Token("OPEN_START_SECTION"         , "{{#", SectionNode),
  new Token("OPEN_START_VERTED_SECTION"  , "{{?", VertedSectionNode),
  new Token("OPEN_START_INVERTED_SECTION", "{{^", InvertedSectionNode),
  new Token("OPEN_START_JSON"            , "{{*", JsonNode),
  new Token("OPEN_START_PARTIAL"         , "{{+", PartialNode),
  new Token("OPEN_ELSE"                  , "{{:", null),
  new Token("OPEN_END_SECTION"           , "{{/", null),
  new Token("OPEN_UNESCAPED_VARIABLE"    , "{{{", UnescapedVariableNode),
  new Token("CLOSE_MUSTACHE3"            , "}}}", null),
  new Token("OPEN_COMMENT"               , "{{-", null),
  new Token("CLOSE_COMMENT"              , "-}}", null),
  new Token("OPEN_VARIABLE"              , "{{" , EscapedVariableNode),
  new Token("CLOSE_MUSTACHE"             , "}}" , null),
  new Token("CHARACTER"                  , "."  , StringNode)
]

# Make the enums mirror Java enums, to have Token.CHARACTER etc.
(() ->
  for token in Token.values
    Token[token.name] = token
)()

# Tokeniser for template parsing.
class TokenStream
  constructor: (string) ->
    @_remainder = string

    @nextToken = null
    @nextContents = null
    @nextLine = new Line(1)
    @advance()

  hasNext: () ->
    return @nextToken?

  advance: () ->
    if @nextContents == '\n'
      @nextLine = new Line(@nextLine.number + 1)

    @nextToken = null
    @nextContents = null

    if @_remainder.length == 0
      return null

    for token in Token.values
      if @_remainder.slice(0, token.text.length) == token.text
        @nextToken = token
        break

    if @nextToken == null
      @nextToken = Token.CHARACTER

    @nextContents = @_remainder.slice(0, @nextToken.text.length)
    @_remainder = @_remainder.slice(@nextToken.text.length)
    return @

  advanceOver: (token, excluded) ->
    if @nextToken != token
      throw new ParseException(
          "Expecting token " + token.name + " but got " + @nextToken.name,
          @nextLine)
    return @advance()
  
  advanceOverNextString: (excluded) ->
    buf = new StringBuilder()
    while @nextToken == Token.CHARACTER and
          (not excluded? or excluded.indexOf(@nextContents) == -1)
      buf.append(@nextContents)
      @advance()
    return buf.toString()

class Handlebar
  # Creates a new {@link Handlebar} parsed from a string.
  constructor: (template) ->
    @source = template
    tokens = new TokenStream(template)
    @_topNode =  @_parseSection(tokens)
    if not @_topNode?
      throw new ParseException("Template is empty", tokens.nextLine)
    if tokens.hasNext()
      throw new ParseSection("There are still tokens remaining, was there an "
                             "end-section without a start-section?",
                             tokens.nextLine)

  _parseSection: (tokens) ->
    nodes = []
    sectionEnded = false

    while tokens.hasNext() && !sectionEnded
      token = tokens.nextToken

      switch token
        when Token.CHARACTER
          startLine = tokens.nextLine
          string = tokens.advanceOverNextString()
          nodes.push(new StringNode(string, startLine, tokens.nextLine))

        when Token.OPEN_VARIABLE, \
             Token.OPEN_UNESCAPED_VARIABLE, \
             Token.OPEN_START_JSON
          id = @_openSectionOrTag(tokens)
          nodes.push(new token.clazz(id, tokens.nextLine))

        when Token.OPEN_START_PARTIAL
          tokens.advance()
          id = new Identifier(tokens.advanceOverNextString(' '),
                              tokens.nextLine)
          partialNode = new PartialNode(id, tokens.nextLine)

          while tokens.nextToken == Token.CHARACTER
            tokens.advance()
            key = tokens.advanceOverNextString(':')
            tokens.advance()
            partialNode.addArgument(
                key,
                new Identifier(tokens.advanceOverNextString(' '),
                               tokens.nextLine))

          tokens.advanceOver(Token.CLOSE_MUSTACHE)
          nodes.push(partialNode)

        when Token.OPEN_START_SECTION
          id = @_openSectionOrTag(tokens)
          section = @_parseSection(tokens)
          @_closeSection(tokens, id)
          if section?
            nodes.push(new SectionNode(id, section))

        when Token.OPEN_START_VERTED_SECTION, \
             Token.OPEN_START_INVERTED_SECTION
          id = @_openSectionOrTag(tokens)
          section = @_parseSection(tokens)
          elseSection = null
          if tokens.nextToken == Token.OPEN_ELSE
            @_openElse(tokens, id)
            elseSection = @_parseSection(tokens)
          @_closeSection(tokens, id)
          if section?
            nodes.push(new token.clazz(id, section))
          if elseSection?
            nodes.push(new (token.elseNodeClass())(id, elseSection))

        when Token.OPEN_COMMENT
          @_advanceOverComment(tokens)

        when Token.OPEN_END_SECTION, \
             Token.OPEN_ELSE
          # Handled after running parseSection within the SECTION cases, so this is a
          # terminating condition. If there *is* an orphaned OPEN_END_SECTION, it will be caught
          # by noticing that there are leftover tokens after termination.
          sectionEnded = true
        
        when Token.CLOSE_MUSTACHE
          throw new ParseException("Orphaned " + tokens.nextToken,
                                   tokens.nextLine)

    for node, i in nodes
      if node instanceof StringNode
        continue

      previousNode = if (i > 0) then nodes[i - 1] else null
      nextNode = if (i < nodes.length - 1) then nodes[i + 1] else null
      renderedNode = null

      if node.getStartLine() != node.getEndLine()
        renderedNode = new BlockNode(node)
        if previousNode?
          previousNode.trimEndingSpaces()
        if nextNode?
          nextNode.trimStartingNewLine()
      else if (node instanceof LeafNode) and
              (not previousNode? or previousNode.endsWithEmptyLine()) and
              (not nextNode? or nextNode.startsWithNewLine())
        indentation = 0
        if previousNode?
          indentation = previousNode.trimEndingSpaces()
        if nextNode?
          nextNode.trimStartingNewLine()
        renderedNode = new IndentedNode(node, indentation)
      else
        renderedNode = new InlineNode(node)

      nodes[i] = renderedNode

    if nodes.length == 0
      return null
    else if nodes.length == 1
      return nodes[0]
    return new NodeCollection(nodes)

  _advanceOverComment: (tokens) ->
    tokens.advanceOver(Token.OPEN_COMMENT)
    depth = 1
    while tokens.hasNext() and depth > 0
      if tokens.nextToken == Token.OPEN_COMMENT
        depth++
      else if tokens.nextToken == Token.CLOSE_COMMENT
        depth--
      tokens.advance()

  _openSectionOrTag: (tokens) ->
    openToken = tokens.nextToken
    tokens.advance()
    id = new Identifier(tokens.advanceOverNextString(), tokens.nextLine)
    if openToken == Token.OPEN_UNESCAPED_VARIABLE
      tokens.advanceOver(Token.CLOSE_MUSTACHE3)
    else
      tokens.advanceOver(Token.CLOSE_MUSTACHE)
    return id

  _closeSection: (tokens, id) ->
    tokens.advanceOver(Token.OPEN_END_SECTION)
    nextString = tokens.advanceOverNextString()
    if nextString.length > 0 and id.toString() != nextString
      throw new ParseException(
          "Start section " + id + " doesn't match end " + nextString,
          tokens.nextLine)
    tokens.advanceOver(Token.CLOSE_MUSTACHE)

  _openElse: (tokens, id) ->
    tokens.advanceOver(Token.OPEN_ELSE)
    nextString = tokens.advanceOverNextString()
    if nextString.length > 0 and id.toString() != nextString
      throw new ParseException(
          "Start section " + id + " doesn't match else " + nextString,
          tokens.nextLine)
    tokens.advanceOver(Token.CLOSE_MUSTACHE)

  render: (contexts...) ->
    globalContexts = []
    for context in contexts
      globalContexts.push(context)
    renderState = new RenderState(globalContexts, [])
    @_topNode.render(renderState)
    return renderState.getResult()

Handlebar.RenderResult = RenderResult
exports.class = Handlebar
