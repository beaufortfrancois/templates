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

# TODO: Allow specifying ints, strings, bools (the latter if not overriden
#       I suppose, the former requiring disallowing pure-int identifiers) as
#       partial arguments.
# TODO: Only have @ while in a loop, and only defined in the top context of
#       the loop.
# TODO: Only transfer global contexts into partials, not the top local.
# TODO: Pragmas for asserting the presence of variables.
# TODO: Don't print \n's in block comments.
# TODO: Some kind of shorthand for e.g.
#           <span class="{{?foo}}foo{{/}}">hello</span>
#       e.g. {{?foo foo}} {{^foo foo}}
# TODO: Escaping control characters somehow. e.g. \{{.
# TODO: Dump warnings-so-far into the output.

import json
import re
import sys

'''Handlebar templates are data binding templates more-than-loosely inspired by
ctemplate. Use like:

  from handlebar import Handlebar

  template = Handlebar('hello {{#foo}}{{bar}}{{/}} world')
  input = {
    'foo': [
      { 'bar': 1 },
      { 'bar': 2 },
      { 'bar': 3 }
    ]
  }
  print(template.render(input).text)

Handlebar will use get() on contexts to return values, so to create custom
getters (for example, something that populates values lazily from keys), just
provide an object with a get() method.

  class CustomContext(object):
    def get(self, key):
      return 10
  print(Handlebar('hello {{world}}').render(CustomContext()).text)

will print 'hello 10'.
'''

class ParseException(Exception):
  '''The exception thrown while parsing a template.
  '''
  def __init__(self, error, line):
    Exception.__init__(self, '%s (line %s)' % (error, line.number))

class _RenderResult(object):
  '''The result of a render operation.
  '''
  def __init__(self, text, errors):
    self.text = text;
    self.errors = errors

class _StringBuilder(object):
  '''Efficiently builds strings.
  '''
  def __init__(self):
    self._buf = []

  def __len__(self):
    self._Collapse()
    return len(self._buf[0])

  def Append(self, string):
    if not isinstance(string, basestring):
      string = str(string)
    self._buf.append(string)

  def ToString(self):
    self._Collapse()
    return self._buf[0]

  def __str__(self):
    return self.ToString()

  def _Collapse(self):
    self._buf = [u''.join(self._buf)]

class _Contexts(object):
  '''Tracks a stack of context objects, providing efficient key/value retrieval.
  '''
  class _Node(object):
    '''A node within the stack. Wraps a real context and maintains the key/value
    pairs seen so far.
    '''
    def __init__(self, value):
      self._value = value
      self._value_has_get = hasattr(value, 'get')
      self._found = {}

    def GetKeys(self):
      '''Returns the list of keys that |_value| contains.
      '''
      return self._found.keys()

    def Get(self, key):
      '''Returns the value for |key|, or None if not found (including if
      |_value| doesn't support key retrieval).
      '''
      if not self._value_has_get:
        return None
      value = self._found.get(key)
      if value is not None:
        return value
      value = self._value.get(key)
      if value is not None:
        self._found[key] = value
      return value

  def __init__(self, globals_):
    '''Initializes with the initial global contexts, listed in order from most
    to least important.
    '''
    self._nodes = map(_Contexts._Node, globals_)
    self._first_local = len(self._nodes)
    self._value_info = {}

  def CreateFromGlobals(self):
    new = _Contexts([])
    new._nodes = self._nodes[:self._first_local]
    new._first_local = self._first_local
    return new

  def Push(self, context):
    self._nodes.append(_Contexts._Node(context))

  def Pop(self):
    node = self._nodes.pop()
    assert len(self._nodes) >= self._first_local
    for found_key in node.GetKeys():
      # [0] is the stack of nodes that |found_key| has been found in.
      self._value_info[found_key][0].pop()

  def GetTopLocal(self):
    if len(self._nodes) == self._first_local:
      return None
    return self._nodes[-1]._value

  def Resolve(self, path):
    # This method is only efficient at finding |key|; if |tail| has a value (and
    # |key| evaluates to an indexable value) we'll need to descend into that.
    key, tail = path.split('.', 1) if '.' in path else (path, None)

    if key == '@':
      found = self._nodes[-1]._value
    else:
      found = self._FindNodeValue(key)

    if tail is None:
      return found

    for part in tail.split('.'):
      if not hasattr(found, 'get'):
        return None
      found = found.get(part)
    return found

  def _FindNodeValue(self, key):
    # |found_node_list| will be all the nodes that |key| has been found in.
    # |checked_node_set| are those that have been checked.
    info = self._value_info.get(key)
    if info is None:
      info = ([], set())
      self._value_info[key] = info
    found_node_list, checked_node_set = info

    # Check all the nodes not yet checked for |key|.
    newly_found = []
    for node in reversed(self._nodes):
      if node in checked_node_set:
        break
      value = node.Get(key)
      if value is not None:
        newly_found.append(node)
      checked_node_set.add(node)

    # The nodes will have been found in reverse stack order. After extending
    # the found nodes, the freshest value will be at the tip of the stack.
    found_node_list.extend(reversed(newly_found))
    if not found_node_list:
      return None

    return found_node_list[-1]._value[key]

class _RenderState(object):
  '''The state of a render call.
  '''
  def __init__(self, name, contexts, _partial_info=None):
    self.text = _StringBuilder()
    self.contexts = contexts
    self._name = name
    self._errors = []
    self._partial_info = _partial_info

  def AddResolutionError(self, id_):
    self._errors.append('Failed to resolve %s in %s' % (id_.GetDescription(),
                                                        self._name))
    partial_info = self._partial_info
    while partial_info is not None:
      render_state, id_ = partial_info
      self._errors.append('  included as %s in %s' % (id_.GetDescription(),
                                                      render_state._name))
      partial_info = render_state._partial_info

  def Copy(self):
    return _RenderState(
        self._name, self.contexts, _partial_info=self._partial_info)

  def ForkPartial(self, custom_name, id_):
    name = custom_name or id_.name
    return _RenderState(
        name, self.contexts.CreateFromGlobals(), _partial_info=(self, id_))

  def Merge(self, render_state, text_transform=None):
    self._errors.extend(render_state._errors)
    text = render_state.text.ToString()
    if text_transform is not None:
      text = text_transform(text)
    self.text.Append(text)

  def GetResult(self):
    return _RenderResult(self.text.ToString(), self._errors);

class _Identifier(object):
  ''' An identifier of the form '@', 'foo.bar.baz', or '@.foo.bar.baz'.
  '''
  def __init__(self, name, line, column):
    self.name = name
    self._line = line
    self._column = column
    if name == '':
      raise ParseException('Empty identifier %s' % self.GetDescription())
    for part in name.split('.'):
      if part != '@' and not re.match('^[a-zA-Z0-9_\\-/]+$', part):
        raise ParseException('Invalid identifier %s' % self.GetDescription())

  def GetDescription(self):
    return '\'%s\' at line %s column %s' % (self.name, self._line, self._column)

  def __str__(self):
    raise ValueError()

class _Line(object):
  def __init__(self, number):
    self.number = number

  def __str__(self):
    return str(self.number)

class _LeafNode(object):
  def __init__(self, start_line, end_line):
    self._start_line = start_line
    self._end_line = end_line

  def StartsWithNewLine(self):
    return False

  def TrimStartingNewLine(self):
    pass

  def TrimEndingSpaces(self):
    return 0

  def TrimEndingNewLine(self):
    pass

  def EndsWithEmptyLine(self):
    return False

  def GetStartLine(self):
    return self._start_line

  def GetEndLine(self):
    return self._end_line

class _DecoratorNode(object):
  def __init__(self, content):
    self._content = content

  def StartsWithNewLine(self):
    return self._content.StartsWithNewLine()

  def TrimStartingNewLine(self):
    self._content.TrimStartingNewLine()

  def TrimEndingSpaces(self):
    return self._content.TrimEndingSpaces()

  def TrimEndingNewLine(self):
    self._content.TrimEndingNewLine()

  def EndsWithEmptyLine(self):
    return self._content.EndsWithEmptyLine()

  def GetStartLine(self):
    return self._content.GetStartLine()

  def GetEndLine(self):
    return self._content.GetEndLine()

class _InlineNode(_DecoratorNode):
  def __init__(self, content):
    _DecoratorNode.__init__(self, content)

  def Render(self, render_state):
    content_render_state = render_state.Copy()
    self._content.Render(content_render_state)
    render_state.Merge(content_render_state,
                       text_transform=lambda text: text.replace('\n', ''))

class _IndentedNode(_DecoratorNode):
  def __init__(self, content, indentation):
    _DecoratorNode.__init__(self, content)
    self._indent_str = ' ' * indentation

  def Render(self, render_state):
    if isinstance(self._content, _CommentNode):
      return
    content_render_state = render_state.Copy()
    self._content.Render(content_render_state)
    def AddIndentation(text):
      buf = _StringBuilder()
      buf.Append(self._indent_str)
      buf.Append(text.replace('\n', '\n%s' % self._indent_str))
      buf.Append('\n')
      return buf.ToString()
    render_state.Merge(content_render_state, text_transform=AddIndentation)

class _BlockNode(_DecoratorNode):
  def __init__(self, content):
    _DecoratorNode.__init__(self, content)
    content.TrimStartingNewLine()
    content.TrimEndingSpaces()

  def Render(self, render_state):
    self._content.Render(render_state)

class _NodeCollection(object):
  def __init__(self, nodes):
    assert nodes
    self._nodes = nodes

  def Render(self, render_state):
    for node in self._nodes:
      node.Render(render_state)

  def StartsWithNewLine(self):
    return self._nodes[0].StartsWithNewLine()

  def TrimStartingNewLine(self):
    self._nodes[0].TrimStartingNewLine()

  def TrimEndingSpaces(self):
    return self._nodes[-1].TrimEndingSpaces()

  def TrimEndingNewLine(self):
    self._nodes[-1].TrimEndingNewLine()

  def EndsWithEmptyLine(self):
    return self._nodes[-1].EndsWithEmptyLine()

  def GetStartLine(self):
    return self._nodes[0].GetStartLine()

  def GetEndLine(self):
    return self._nodes[-1].GetEndLine()

class _StringNode(object):
  ''' Just a string.
  '''
  def __init__(self, string, start_line, end_line):
    self._string = string
    self._start_line = start_line
    self._end_line = end_line

  def Render(self, render_state):
    render_state.text.Append(self._string)

  def StartsWithNewLine(self):
    return self._string.startswith('\n')

  def TrimStartingNewLine(self):
    if self.StartsWithNewLine():
      self._string = self._string[1:]

  def TrimEndingSpaces(self):
    original_length = len(self._string)
    self._string = self._string[:self._LastIndexOfSpaces()]
    return original_length - len(self._string)

  def TrimEndingNewLine(self):
    if self._string.endswith('\n'):
      self._string = self._string[:len(self._string) - 1]

  def EndsWithEmptyLine(self):
    index = self._LastIndexOfSpaces()
    return index == 0 or self._string[index - 1] == '\n'

  def _LastIndexOfSpaces(self):
    index = len(self._string)
    while index > 0 and self._string[index - 1] == ' ':
      index -= 1
    return index

  def GetStartLine(self):
    return self._start_line

  def GetEndLine(self):
    return self._end_line

class _EscapedVariableNode(_LeafNode):
  ''' {{foo}}
  '''
  def __init__(self, id_, line):
    _LeafNode.__init__(self, line, line)
    self._id = id_

  def Render(self, render_state):
    value = render_state.contexts.Resolve(self._id.name)
    if value is None:
      render_state.AddResolutionError(self._id)
      return
    string = value if isinstance(value, basestring) else str(value)
    render_state.text.Append(string.replace('&', '&amp;')
                                   .replace('<', '&lt;')
                                   .replace('>', '&gt;'))

class _UnescapedVariableNode(_LeafNode):
  ''' {{{foo}}}
  '''
  def __init__(self, id_, line):
    _LeafNode.__init__(self, line, line)
    self._id = id_

  def Render(self, render_state):
    value = render_state.contexts.Resolve(self._id.name)
    if value is None:
      render_state.AddResolutionError(self._id)
      return
    string = value if isinstance(value, basestring) else str(value)
    render_state.text.Append(string)

class _CommentNode(_LeafNode):
  '''{{- This is a comment -}}
  An empty placeholder node for correct indented rendering behaviour.
  '''
  def __init__(self, start_line, end_line):
    _LeafNode.__init__(self, start_line, end_line)

  def Render(self, render_state):
    pass

class _SectionNode(_DecoratorNode):
  ''' {{#foo}} ... {{/}}
  '''
  def __init__(self, id_, content):
    _DecoratorNode.__init__(self, content)
    self._id = id_

  def Render(self, render_state):
    value = render_state.contexts.Resolve(self._id.name)
    if isinstance(value, list):
      for item in value:
        # Always push context, even if it's not "valid", since we want to
        # be able to refer to items in a list such as [1,2,3] via @.
        render_state.contexts.Push(item)
        self._content.Render(render_state)
        render_state.contexts.Pop()
    elif hasattr(value, 'get'):
      render_state.contexts.Push(value)
      self._content.Render(render_state)
      render_state.contexts.Pop()
    else:
      render_state.AddResolutionError(self._id)

class _VertedSectionNode(_DecoratorNode):
  ''' {{?foo}} ... {{/}}
  '''
  def __init__(self, id_, content):
    _DecoratorNode.__init__(self, content)
    self._id = id_

  def Render(self, render_state):
    value = render_state.contexts.Resolve(self._id.name)
    if _VertedSectionNode.ShouldRender(value):
      render_state.contexts.Push(value)
      self._content.Render(render_state)
      render_state.contexts.Pop()

  @staticmethod
  def ShouldRender(value):
    if value is None:
      return False
    if isinstance(value, bool):
      return value
    if isinstance(value, list):
      return len(value) > 0
    return True

class _InvertedSectionNode(_DecoratorNode):
  ''' {{^foo}} ... {{/}}
  '''
  def __init__(self, id_, content):
    _DecoratorNode.__init__(self, content)
    self._id = id_

  def Render(self, render_state):
    value = render_state.contexts.Resolve(self._id.name)
    if not _VertedSectionNode.ShouldRender(value):
      self._content.Render(render_state)

class _JsonNode(_LeafNode):
  ''' {{*foo}}
  '''
  def __init__(self, id_, line):
    _LeafNode.__init__(self, line, line)
    self._id = id_

  def Render(self, render_state):
    value = render_state.contexts.Resolve(self._id.name)
    if value is None:
      render_state.AddResolutionError(self._id)
      return
    render_state.text.Append(json.dumps(value, separators=(',',':')))

class _PartialNode(_LeafNode):
  ''' {{+foo}}
  '''
  def __init__(self, id_, line):
    _LeafNode.__init__(self, line, line)
    self._id = id_
    self._args = None
    self._local_context_id = None

  def Render(self, render_state):
    value = render_state.contexts.Resolve(self._id.name)
    if value is None:
      render_state.AddResolutionError(self._id)
      return
    if not isinstance(value, Handlebar):
      render_state.AddResolutionError(self._id)
      return

    partial_render_state = render_state.ForkPartial(value._name, self._id)

    # TODO: Don't do this. Force callers to do this by specifying an @ argument.
    top_local = render_state.contexts.GetTopLocal()
    if top_local is not None:
      partial_render_state.contexts.Push(top_local)

    if self._args is not None:
      arg_context = {}
      for key, value_id in self._args.items():
        context = render_state.contexts.Resolve(value_id.name)
        if context is not None:
          arg_context[key] = context
      partial_render_state.contexts.Push(arg_context)

    if self._local_context_id is not None:
      local_context = render_state.contexts.Resolve(self._local_context_id.name)
      if local_context is not None:
        partial_render_state.contexts.Push(local_context)

    value._top_node.Render(partial_render_state)

    render_state.Merge(
        partial_render_state,
        text_transform=lambda text: text[:-1] if text.endswith('\n') else text)

  def AddArgument(self, key, id_):
    if self._args is None:
      self._args = {}
    self._args[key] = id_

  def SetLocalContext(self, id_):
    self._local_context_id = id_

# List of tokens in order of longest to shortest, to avoid any prefix matching
# issues.
TokenValues = []

class _Token(object):
  ''' The tokens that can appear in a template.
  '''
  class Data(object):
    def __init__(self, name, text, clazz):
      self.name = name
      self.text = text
      self.clazz = clazz
      TokenValues.append(self)

    def ElseNodeClass(self):
      if self.clazz == _VertedSectionNode:
        return _InvertedSectionNode
      if self.clazz == _InvertedSectionNode:
        return _VertedSectionNode
      raise ValueError('%s cannot have an else clause.' % self.clazz)

  OPEN_START_SECTION          = Data('OPEN_START_SECTION'         , '{{#', _SectionNode)
  OPEN_START_VERTED_SECTION   = Data('OPEN_START_VERTED_SECTION'  , '{{?', _VertedSectionNode)
  OPEN_START_INVERTED_SECTION = Data('OPEN_START_INVERTED_SECTION', '{{^', _InvertedSectionNode)
  OPEN_START_JSON             = Data('OPEN_START_JSON'            , '{{*', _JsonNode)
  OPEN_START_PARTIAL          = Data('OPEN_START_PARTIAL'         , '{{+', _PartialNode)
  OPEN_ELSE                   = Data('OPEN_ELSE'                  , '{{:', None)
  OPEN_END_SECTION            = Data('OPEN_END_SECTION'           , '{{/', None)
  OPEN_UNESCAPED_VARIABLE     = Data('OPEN_UNESCAPED_VARIABLE'    , '{{{', _UnescapedVariableNode)
  CLOSE_MUSTACHE3             = Data('CLOSE_MUSTACHE3'            , '}}}', None)
  OPEN_COMMENT                = Data('OPEN_COMMENT'               , '{{-', _CommentNode)
  CLOSE_COMMENT               = Data('CLOSE_COMMENT'              , '-}}', None)
  OPEN_VARIABLE               = Data('OPEN_VARIABLE'              , '{{' , _EscapedVariableNode)
  CLOSE_MUSTACHE              = Data('CLOSE_MUSTACHE'             , '}}' , None)
  CHARACTER                   = Data('CHARACTER'                  , '.'  , None)

class _TokenStream(object):
  ''' Tokeniser for template parsing.
  '''
  def __init__(self, string):
    self._remainder = string
    self.next_token = None
    self.next_contents = None
    self.next_line = _Line(1)
    self.next_column = 0
    self.Advance()

  def HasNext(self):
    return self.next_token is not None

  def Advance(self):
    if self.next_contents == '\n':
      self.next_line = _Line(self.next_line.number + 1)
      self.next_column = 0
    elif self.next_token is not None:
      self.next_column += len(self.next_token.text)

    self.next_token = None
    self.next_contents = None

    if self._remainder == '':
      return None

    for token in TokenValues:
      if self._remainder.startswith(token.text):
        self.next_token = token
        break

    if not self.next_token:
      self.next_token = _Token.CHARACTER

    self.next_contents = self._remainder[0:len(self.next_token.text)]
    self._remainder = self._remainder[len(self.next_token.text):]
    return self

  def AdvanceOver(self, token):
    if self.next_token != token:
      raise ParseException(
          'Expecting token ' + token.name + ' but got ' + self.next_token.name,
          self.next_line)
    return self.Advance()

  def AdvanceOverNextString(self, excluded=''):
    buf = _StringBuilder()
    while self.next_token == _Token.CHARACTER and \
          excluded.find(self.next_contents) == -1:
      buf.Append(self.next_contents)
      self.Advance()
    return buf.ToString()

  def AdvanceToNextWhitespace(self):
    return self.AdvanceOverNextString(excluded=' \n\r\t')

  def SkipWhitespace(self):
    while len(self.next_contents) > 0 and \
          ' \n\r\t'.find(self.next_contents) >= 0:
      self.Advance()

class Handlebar(object):
  ''' A handlebar template.
  '''
  def __init__(self, template, name=None):
    self.source = template
    self._name = name
    tokens = _TokenStream(template)
    self._top_node = self._ParseSection(tokens)
    if not self._top_node:
      raise ParseException('Template is empty', tokens.next_line)
    if tokens.HasNext():
      raise ParseException('There are still tokens remaining, '
                           'was there an end-section without a start-section:',
                           tokens.next_line)

  def _ParseSection(self, tokens):
    nodes = []
    section_ended = False

    while tokens.HasNext() and not section_ended:
      token = tokens.next_token

      if token == _Token.CHARACTER:
        start_line = tokens.next_line
        string = tokens.AdvanceOverNextString()
        nodes.append(_StringNode(string, start_line, tokens.next_line))
      elif token == _Token.OPEN_VARIABLE or \
           token == _Token.OPEN_UNESCAPED_VARIABLE or \
           token == _Token.OPEN_START_JSON:
        id_ = self._OpenSectionOrTag(tokens)
        nodes.append(token.clazz(id_, tokens.next_line))
      elif token == _Token.OPEN_START_PARTIAL:
        tokens.Advance()
        column_start = tokens.next_column + 1
        id_ = _Identifier(tokens.AdvanceToNextWhitespace(),
                          tokens.next_line,
                          column_start)
        partial_node = _PartialNode(id_, tokens.next_line)
        while tokens.next_token == _Token.CHARACTER:
          tokens.SkipWhitespace()
          key = tokens.AdvanceOverNextString(excluded=':')
          tokens.Advance()
          column_start = tokens.next_column + 1
          id_ = _Identifier(tokens.AdvanceToNextWhitespace(),
                            tokens.next_line,
                            column_start)
          if key == '@':
            partial_node.SetLocalContext(id_)
          else:
            partial_node.AddArgument(key, id_)
        tokens.AdvanceOver(_Token.CLOSE_MUSTACHE)
        nodes.append(partial_node)
      elif token == _Token.OPEN_START_SECTION:
        id_ = self._OpenSectionOrTag(tokens)
        section = self._ParseSection(tokens)
        self._CloseSection(tokens, id_)
        if section:
          nodes.append(_SectionNode(id_, section))
      elif token == _Token.OPEN_START_VERTED_SECTION or \
           token == _Token.OPEN_START_INVERTED_SECTION:
        id_ = self._OpenSectionOrTag(tokens)
        section = self._ParseSection(tokens)
        else_section = None
        if tokens.next_token == _Token.OPEN_ELSE:
          self._OpenElse(tokens, id_)
          else_section = self._ParseSection(tokens)
        self._CloseSection(tokens, id_)
        if section:
          nodes.append(token.clazz(id_, section))
        if else_section:
          nodes.append(token.ElseNodeClass()(id_, else_section))
      elif token == _Token.OPEN_COMMENT:
        start_line = tokens.next_line
        self._AdvanceOverComment(tokens)
        nodes.append(_CommentNode(start_line, tokens.next_line))
      elif token == _Token.OPEN_END_SECTION or \
           token == _Token.OPEN_ELSE:
        # Handled after running parseSection within the SECTION cases, so this
        # is a terminating condition. If there *is* an orphaned
        # OPEN_END_SECTION, it will be caught by noticing that there are
        # leftover tokens after termination.
        section_ended = True
      elif _Token.CLOSE_MUSTACHE:
        raise ParseException('Orphaned %s' % tokens.next_token.name,
                             tokens.next_line)

    for i, node in enumerate(nodes):
      if isinstance(node, _StringNode):
        continue

      previous_node = nodes[i - 1] if i > 0 else None
      next_node = nodes[i + 1] if i < len(nodes) - 1 else None
      rendered_node = None

      if node.GetStartLine() != node.GetEndLine():
        rendered_node = _BlockNode(node)
        if previous_node:
          previous_node.TrimEndingSpaces()
        if next_node:
          next_node.TrimStartingNewLine()
      elif (isinstance(node, _LeafNode) and
            (not previous_node or previous_node.EndsWithEmptyLine()) and
            (not next_node or next_node.StartsWithNewLine())):
        indentation = 0
        if previous_node:
          indentation = previous_node.TrimEndingSpaces()
        if next_node:
          next_node.TrimStartingNewLine()
        rendered_node = _IndentedNode(node, indentation)
      else:
        rendered_node = _InlineNode(node)

      nodes[i] = rendered_node

    if len(nodes) == 0:
      return None
    if len(nodes) == 1:
      return nodes[0]
    return _NodeCollection(nodes)

  def _AdvanceOverComment(self, tokens):
    tokens.AdvanceOver(_Token.OPEN_COMMENT)
    comment = _StringBuilder()
    depth = 1
    while tokens.HasNext() and depth > 0:
      if tokens.next_token == _Token.OPEN_COMMENT:
        depth += 1
      elif tokens.next_token == _Token.CLOSE_COMMENT:
        depth -= 1
      if depth > 0:
        comment.Append(tokens.next_contents)
      tokens.Advance()
    return comment.ToString()

  def _OpenSectionOrTag(self, tokens):
    open_token = tokens.next_token
    tokens.Advance()
    column_start = tokens.next_column + 1
    id_ = _Identifier(tokens.AdvanceOverNextString(),
                      tokens.next_line,
                      column_start)
    if open_token == _Token.OPEN_UNESCAPED_VARIABLE:
      tokens.AdvanceOver(_Token.CLOSE_MUSTACHE3)
    else:
      tokens.AdvanceOver(_Token.CLOSE_MUSTACHE)
    return id_

  def _CloseSection(self, tokens, id_):
    tokens.AdvanceOver(_Token.OPEN_END_SECTION)
    next_string = tokens.AdvanceOverNextString()
    if next_string != '' and next_string != id_.name:
      raise ParseException(
          'Start section %s doesn\'t match end %s' % (id_, next_string))
    tokens.AdvanceOver(_Token.CLOSE_MUSTACHE)

  def _OpenElse(self, tokens, id_):
    tokens.AdvanceOver(_Token.OPEN_ELSE)
    next_string = tokens.AdvanceOverNextString()
    if next_string != '' and next_string != id_.name:
      raise ParseException(
          'Start section %s doesn\'t match else %s' % (id_, next_string))
    tokens.AdvanceOver(_Token.CLOSE_MUSTACHE)

  def Render(self, *contexts):
    '''Renders this template given a variable number of contexts to read out
    values from (such as those appearing in {{foo}}).
    '''
    name = self._name or '<root>'
    render_state = _RenderState(name, _Contexts(contexts))
    self._top_node.Render(render_state)
    return render_state.GetResult()

  def render(self, *contexts):
    return self.Render(*contexts)
