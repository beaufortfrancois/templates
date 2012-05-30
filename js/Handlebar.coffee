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

ParseException = Error

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

  append: (s) ->
    @_buffer.push(s.toString())

  toString: () ->
    @_buffer.join('')

class PathIdentifier
  constructor: (name) ->
    if name.length == 0
      throw new ParseException("Cannot have empty identifiers")
    if not /^[a-zA-Z0-9._]*$/.test(name)
      throw new ParseException(name + " is not a valid identifier")
    @path = name.split(".")

  resolve: (contexts, errors) ->
    resolved = null
    for context in contexts
      if not context?
        continue
      resolved = @_resolveFrom(context)
      if resolved?
        return resolved
    Handlebar.renderError(errors, "Couldn't resolve identifier ", @path, " in ", contexts)
    return null

  _resolveFrom: (context) ->
    result = context
    for next in @path
      if not result?
        return null
      result = result[next]
    return result

  toString: () ->
    return @path.join('.')

class ThisIdentifier
  constructor: () ->

  resolve: (contexts, errors) ->
    return contexts[0]

  toString: () ->
    return '@'

# Nodes which are "self closing", e.g. {{foo}}, {{*foo}}.
class SelfClosingNode
  init: (id) ->
    @id = id

# Nodes which are not self closing, and have 0..n children.
class HasChildrenNode
  init: (id, children) ->
    @id = id
    @children = children

# Just a string.
class StringNode
  constructor: (@string) ->

  render: (buf, contexts, errors) ->
    buf.append(@string)

# {{foo}}
class EscapedVariableNode extends SelfClosingNode
  render: (buf, contexts, errors) ->
    value = @id.resolve(contexts, errors)
    if value?
      buf.append(@_htmlEscape(value.toString()))

  _htmlEscape: (unescaped) ->
    escaped = new StringBuilder()
    for c in unescaped
      switch c
        when '<' then escaped.append("&lt;")
        when '>' then escaped.append("&gt;")
        when '&' then escaped.append("&amp;")
        else escaped.append(c)
    return escaped.toString()

# {{{foo}}}
class UnescapedVariableNode extends SelfClosingNode
  render: (buf, contexts, errors) ->
    value = @id.resolve(contexts, errors)
    if value?
      buf.append(value)

# {{#foo}} ... {{/}}
class SectionNode extends HasChildrenNode
  render: (buf, contexts, errors) ->
    value = @id.resolve(contexts, errors)
    if not value?
      return

    type = typeof value
    if type == null
      # Nothing.
    else if type == 'boolean' or type == 'number' or type == 'string'
      Handlebar.renderError(errors, "{{#", @id, "}} cannot be rendered with a " + type)
    else if value instanceof Array
      for item in value
        contexts.unshift(item)
        Handlebar.renderNodes(buf, @children, contexts, errors)
        contexts.shift()
    else
      contexts.unshift(value)
      Handlebar.renderNodes(buf, @children, contexts, errors)
      contexts.shift()

# {{?foo}} ... {{/}}
class VertedSectionNode extends HasChildrenNode
  render: (buf, contexts, errors) ->
    value = @id.resolve(contexts, errors)
    if value? and VertedSectionNode.shouldRender(value)
      contexts.unshift(value)
      Handlebar.renderNodes(buf, @children, contexts, errors)
      contexts.shift()

VertedSectionNode.shouldRender = (value) ->
  type = typeof value
  if type == 'boolean'
    return value
  else if type == 'number'
    return value > 0
  else if type == 'string'
    return value.length > 0
  else if value instanceof Array
    return value.length > 0
  else if type == 'object'
    return Object.keys(value).length > 0
  throw new Error("Unhandled type: " + type)

# {{^foo}} ... {{/}}
class InvertedSectionNode extends HasChildrenNode
  render: (buf, contexts, errors) ->
    value = @id.resolve(contexts, errors)
    if not value? or !VertedSectionNode.shouldRender(value)
      Handlebar.renderNodes(buf, @children, contexts, errors)

# {{*foo}}
class JsonNode extends SelfClosingNode
  render: (buf, contexts, errors) ->
    value = @id.resolve(contexts, errors)
    if value?
      buf.append(JSON.stringify(value))

# {{+foo}}
class PartialNode extends SelfClosingNode
  render: (buf, contexts, errors) ->
    value = @id.resolve(contexts, errors)
    if not value instanceof Handlebar
      Handlebar.renderError(errors, id, " didn't resolve to a Handlebar")
      return
    Handlebar.renderNodes(buf, value.nodes, contexts, errors)

# {{:foo}}
class SwitchNode
  constructor: (@id) ->
    @_cases = {}

  addCase: (caseValue, caseNode) ->
    @_cases[caseValue] = caseNode

  render: (buf, contexts, errors) ->
    value = @id.resolve(contexts, errors)
    if not value?
      Handlebar.renderError(errors, id, " didn't resolve to any value")
      return
    if typeof(value) != 'string'
      Handlebar.renderError(errors, id, " didn't resolve to a String, instead " + typeof(value))
      return
    caseNode = @_cases[value]
    if caseNode?
      caseNode.render(buf, contexts, errors)

# {{=foo}}
class CaseNode
  constructor: (@children) ->

  render: (buf, contexts, errors) ->
    for child in @children
      child.render(buf, contexts, errors)

class Token
  constructor: (@name, @text, @clazz) ->

# List in order of longest to shortest, to avoid any prefix matching issues.
Token.values = [
  new Token("OPEN_START_SECTION"         , "{{#", SectionNode),
  new Token("OPEN_START_VERTED_SECTION"  , "{{?", VertedSectionNode),
  new Token("OPEN_START_INVERTED_SECTION", "{{^", InvertedSectionNode),
  new Token("OPEN_START_JSON"            , "{{*", JsonNode),
  new Token("OPEN_START_PARTIAL"         , "{{+", PartialNode),
  new Token("OPEN_START_SWITCH"          , "{{:", SwitchNode),
  new Token("OPEN_CASE"                  , "{{=", CaseNode),
  new Token("OPEN_END_SECTION"           , "{{/", null),
  new Token("OPEN_UNESCAPED_VARIABLE"    , "{{{", UnescapedVariableNode),
  new Token("CLOSE_MUSTACHE3"            , "}}}", null),
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
    @nextToken = null
    @_remainder = string
    @_nextContents = null
    @advance()

  hasNext: () ->
    return @nextToken?

  advanceOver: (token) ->
    if @nextToken != token
      throw new ParseException(
          "Expecting token " + token.name + " but got " + @nextToken.name)
    return @advance()

  advance: () ->
    @nextToken = null
    @_nextContents = null

    if @_remainder.length == 0
      return null

    for token in Token.values
      if @_remainder.slice(0, token.text.length) == token.text
        @nextToken = token
        break

    if @nextToken == null
      @nextToken = Token.CHARACTER

    @_nextContents = @_remainder.slice(0, @nextToken.text.length)
    @_remainder = @_remainder.slice(@nextToken.text.length)
    return @nextToken

  nextString: () ->
    buf = new StringBuilder()
    while @nextToken == Token.CHARACTER
      buf.append(@_nextContents)
      @advance()
    return buf.toString()

createIdentifier = (path) ->
  if path == '@'
    return new ThisIdentifier()
  else
    return new PathIdentifier(path)

class Handlebar
  # Creates a new {@link Handlebar} parsed from a string.
  constructor: (template) ->
    @nodes = []
    @_parseTemplate(template, @nodes)

  _parseTemplate: (template, nodes) ->
    tokens = new TokenStream(template)
    @_parseSection(tokens, nodes)
    if tokens.hasNext()
      throw new ParseException(
          "There are still tokens remaining, was there an end-section without a start-section?")

  _parseSection: (tokens, nodes) ->
    sectionEnded = false
    while tokens.hasNext() && !sectionEnded
      switch tokens.nextToken
        when Token.CHARACTER
          nodes.push(new StringNode(tokens.nextString()))

        when Token.OPEN_VARIABLE, \
             Token.OPEN_UNESCAPED_VARIABLE, \
             Token.OPEN_START_JSON, \
             Token.OPEN_START_PARTIAL
          token = tokens.nextToken
          id = @_openSection(tokens)

          try
            node = new token.clazz()
            node.init(id)
            nodes.push(node)
          catch e
            console.warn(e)
            throw new ParseException(e.message)

        when Token.OPEN_START_SECTION, \
             Token.OPEN_START_VERTED_SECTION, \
             Token.OPEN_START_INVERTED_SECTION
          token = tokens.nextToken
          id = @_openSection(tokens)

          children = []
          @_parseSection(tokens, children)
          @_closeSection(tokens, id)

          try
            node = new token.clazz()
            node.init(id, children)
            nodes.push(node)
          catch e
            console.warn(e)
            throw new ParseException(e.message)

        when Token.OPEN_START_SWITCH
          id = @_openSection(tokens)
          # Chew up anything between here and the first case (or the closing of a section, if
          # needs be).
          # TODO: this wouldn't be necessary if we did the blank line optimisation thing.
          while tokens.nextToken == Token.CHARACTER
            tokens.advanceOver(Token.CHARACTER)

          switchNode = new SwitchNode(id)
          nodes.push(switchNode)

          while tokens.hasNext() && tokens.nextToken == Token.OPEN_CASE
            tokens.advanceOver(Token.OPEN_CASE)
            caseValue = tokens.nextString()
            tokens.advanceOver(Token.CLOSE_MUSTACHE)

            caseChildren = []
            # TODO: make parseSection take terminating nodes, pass in OPEN_CASE.
            @_parseSection(tokens, caseChildren)

            switchNode.addCase(caseValue, new CaseNode(caseChildren))

          @_closeSection(tokens, id)
        
        when Token.OPEN_CASE
          # See below.
          sectionEnded = true

        when Token.OPEN_END_SECTION
          # Handled after running parseSection within the SECTION cases, so this is a
          # terminating condition. If there *is* an orphaned OPEN_END_SECTION, it will be caught
          # by noticing that there are leftover tokens after termination.
          sectionEnded = true
        
        when Token.CLOSE_MUSTACHE
          throw new ParseException("Orphaned " + tokens.nextToken)

  _openSection: (tokens) ->
    openToken = tokens.nextToken
    tokens.advance()
    id = createIdentifier(tokens.nextString())
    if openToken == Token.OPEN_UNESCAPED_VARIABLE
      tokens.advanceOver(Token.CLOSE_MUSTACHE3)
    else
      tokens.advanceOver(Token.CLOSE_MUSTACHE)
    return id

  _closeSection: (tokens, id) ->
    tokens.advanceOver(Token.OPEN_END_SECTION)
    nextString = tokens.nextString()
    if nextString.length > 0 && id.toString() != nextString
      throw new ParseException(
          "Start section " + id + " doesn't match end section " + nextString)
    tokens.advanceOver(Token.CLOSE_MUSTACHE)

  # Renders the template given some number of objects to take variables from.
  render: (contexts...) ->
    contextDeque = []
    for context in contexts
      contextDeque.push(context)
    buf = new StringBuilder()
    errors = []
    Handlebar.renderNodes(buf, @nodes, contextDeque, errors)
    return new RenderResult(buf.toString(), errors)

Handlebar.renderNodes = (buf, nodes, contexts, errors) ->
  for node in nodes
    node.render(buf, contexts, errors)
  
Handlebar.renderError = (errors, messages...) ->
  if not errors?
    return
  buf = new StringBuilder()
  for message in messages
    buf.append(message)
  errors.push(buf.toString())

Handlebar.RenderResult = RenderResult
exports.class = Handlebar
