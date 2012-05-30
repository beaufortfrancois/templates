(function() {
  var CaseNode, EscapedVariableNode, HasChildrenNode, InvertedSectionNode, JsonNode, ParseException, PartialNode, PathIdentifier, RenderResult, SectionNode, SelfClosingNode, StringBuilder, StringNode, SwitchNode, ThisIdentifier, Token, TokenStream, Handlebar, UnescapedVariableNode, VertedSectionNode, createIdentifier;
  var __hasProp = Object.prototype.hasOwnProperty, __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor; child.__super__ = parent.prototype; return child; }, __slice = Array.prototype.slice;

  ParseException = Error;

  RenderResult = (function() {

    function RenderResult(text, errors) {
      this.text = text;
      this.errors = errors;
    }

    RenderResult.prototype.appendTo = function(element) {
      var tempElement, _results;
      tempElement = document.createElement(element.tagName);
      tempElement.innerHTML = this.text;
      _results = [];
      while (tempElement.childNodes.length > 0) {
        _results.push(element.appendChild(tempElement.firstChild));
      }
      return _results;
    };

    RenderResult.prototype.insertBefore = function(element) {
      var parent, tempElement, _results;
      parent = element.parentElement;
      tempElement = document.createElement(parent.tagName);
      tempElement.innerHTML = this.text;
      _results = [];
      while (tempElement.childNodes.length > 0) {
        _results.push(parent.insertBefore(tempElement.firstChild, element));
      }
      return _results;
    };

    return RenderResult;

  })();

  StringBuilder = (function() {

    function StringBuilder() {
      this._buffer = [];
    }

    StringBuilder.prototype.append = function(s) {
      return this._buffer.push(s.toString());
    };

    StringBuilder.prototype.toString = function() {
      return this._buffer.join('');
    };

    return StringBuilder;

  })();

  PathIdentifier = (function() {

    function PathIdentifier(name) {
      if (name.length === 0) {
        throw new ParseException("Cannot have empty identifiers");
      }
      if (!/^[a-zA-Z0-9._]*$/.test(name)) {
        throw new ParseException(name + " is not a valid identifier");
      }
      this.path = name.split(".");
    }

    PathIdentifier.prototype.resolve = function(contexts, errors) {
      var context, resolved, _i, _len;
      resolved = null;
      for (_i = 0, _len = contexts.length; _i < _len; _i++) {
        context = contexts[_i];
        if (!(context != null)) continue;
        resolved = this._resolveFrom(context);
        if (resolved != null) return resolved;
      }
      Handlebar.renderError(errors, "Couldn't resolve identifier ", this.path, " in ", contexts);
      return null;
    };

    PathIdentifier.prototype._resolveFrom = function(context) {
      var next, result, _i, _len, _ref;
      result = context;
      _ref = this.path;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        next = _ref[_i];
        if (!(result != null)) return null;
        result = result[next];
      }
      return result;
    };

    PathIdentifier.prototype.toString = function() {
      return this.path.join('.');
    };

    return PathIdentifier;

  })();

  ThisIdentifier = (function() {

    function ThisIdentifier() {}

    ThisIdentifier.prototype.resolve = function(contexts, errors) {
      return contexts[0];
    };

    ThisIdentifier.prototype.toString = function() {
      return '@';
    };

    return ThisIdentifier;

  })();

  SelfClosingNode = (function() {

    function SelfClosingNode() {}

    SelfClosingNode.prototype.init = function(id) {
      return this.id = id;
    };

    return SelfClosingNode;

  })();

  HasChildrenNode = (function() {

    function HasChildrenNode() {}

    HasChildrenNode.prototype.init = function(id, children) {
      this.id = id;
      return this.children = children;
    };

    return HasChildrenNode;

  })();

  StringNode = (function() {

    function StringNode(string) {
      this.string = string;
    }

    StringNode.prototype.render = function(buf, contexts, errors) {
      return buf.append(this.string);
    };

    return StringNode;

  })();

  EscapedVariableNode = (function() {

    __extends(EscapedVariableNode, SelfClosingNode);

    function EscapedVariableNode() {
      EscapedVariableNode.__super__.constructor.apply(this, arguments);
    }

    EscapedVariableNode.prototype.render = function(buf, contexts, errors) {
      var value;
      value = this.id.resolve(contexts, errors);
      if (value != null) return buf.append(this._htmlEscape(value.toString()));
    };

    EscapedVariableNode.prototype._htmlEscape = function(unescaped) {
      var c, escaped, _i, _len;
      escaped = new StringBuilder();
      for (_i = 0, _len = unescaped.length; _i < _len; _i++) {
        c = unescaped[_i];
        switch (c) {
          case '<':
            escaped.append("&lt;");
            break;
          case '>':
            escaped.append("&gt;");
            break;
          case '&':
            escaped.append("&amp;");
            break;
          default:
            escaped.append(c);
        }
      }
      return escaped.toString();
    };

    return EscapedVariableNode;

  })();

  UnescapedVariableNode = (function() {

    __extends(UnescapedVariableNode, SelfClosingNode);

    function UnescapedVariableNode() {
      UnescapedVariableNode.__super__.constructor.apply(this, arguments);
    }

    UnescapedVariableNode.prototype.render = function(buf, contexts, errors) {
      var value;
      value = this.id.resolve(contexts, errors);
      if (value != null) return buf.append(value);
    };

    return UnescapedVariableNode;

  })();

  SectionNode = (function() {

    __extends(SectionNode, HasChildrenNode);

    function SectionNode() {
      SectionNode.__super__.constructor.apply(this, arguments);
    }

    SectionNode.prototype.render = function(buf, contexts, errors) {
      var item, type, value, _i, _len, _results;
      value = this.id.resolve(contexts, errors);
      if (!(value != null)) return;
      type = typeof value;
      if (type === null) {} else if (type === 'boolean' || type === 'number' || type === 'string') {
        return Handlebar.renderError(errors, "{{#", this.id, "}} cannot be rendered with a " + type);
      } else if (value instanceof Array) {
        _results = [];
        for (_i = 0, _len = value.length; _i < _len; _i++) {
          item = value[_i];
          contexts.unshift(item);
          Handlebar.renderNodes(buf, this.children, contexts, errors);
          _results.push(contexts.shift());
        }
        return _results;
      } else {
        contexts.unshift(value);
        Handlebar.renderNodes(buf, this.children, contexts, errors);
        return contexts.shift();
      }
    };

    return SectionNode;

  })();

  VertedSectionNode = (function() {

    __extends(VertedSectionNode, HasChildrenNode);

    function VertedSectionNode() {
      VertedSectionNode.__super__.constructor.apply(this, arguments);
    }

    VertedSectionNode.prototype.render = function(buf, contexts, errors) {
      var value;
      value = this.id.resolve(contexts, errors);
      if ((value != null) && VertedSectionNode.shouldRender(value)) {
        contexts.unshift(value);
        Handlebar.renderNodes(buf, this.children, contexts, errors);
        return contexts.shift();
      }
    };

    return VertedSectionNode;

  })();

  VertedSectionNode.shouldRender = function(value) {
    var type;
    type = typeof value;
    if (type === 'boolean') {
      return value;
    } else if (type === 'number') {
      return value > 0;
    } else if (type === 'string') {
      return value.length > 0;
    } else if (value instanceof Array) {
      return value.length > 0;
    } else if (type === 'object') {
      return Object.keys(value).length > 0;
    }
    throw new Error("Unhandled type: " + type);
  };

  InvertedSectionNode = (function() {

    __extends(InvertedSectionNode, HasChildrenNode);

    function InvertedSectionNode() {
      InvertedSectionNode.__super__.constructor.apply(this, arguments);
    }

    InvertedSectionNode.prototype.render = function(buf, contexts, errors) {
      var value;
      value = this.id.resolve(contexts, errors);
      if (!(value != null) || !VertedSectionNode.shouldRender(value)) {
        return Handlebar.renderNodes(buf, this.children, contexts, errors);
      }
    };

    return InvertedSectionNode;

  })();

  JsonNode = (function() {

    __extends(JsonNode, SelfClosingNode);

    function JsonNode() {
      JsonNode.__super__.constructor.apply(this, arguments);
    }

    JsonNode.prototype.render = function(buf, contexts, errors) {
      var value;
      value = this.id.resolve(contexts, errors);
      if (value != null) return buf.append(JSON.stringify(value));
    };

    return JsonNode;

  })();

  PartialNode = (function() {

    __extends(PartialNode, SelfClosingNode);

    function PartialNode() {
      PartialNode.__super__.constructor.apply(this, arguments);
    }

    PartialNode.prototype.render = function(buf, contexts, errors) {
      var value;
      value = this.id.resolve(contexts, errors);
      if (!value instanceof Handlebar) {
        Handlebar.renderError(errors, id, " didn't resolve to a Handlebar");
        return;
      }
      return Handlebar.renderNodes(buf, value.nodes, contexts, errors);
    };

    return PartialNode;

  })();

  SwitchNode = (function() {

    function SwitchNode(id) {
      this.id = id;
      this._cases = {};
    }

    SwitchNode.prototype.addCase = function(caseValue, caseNode) {
      return this._cases[caseValue] = caseNode;
    };

    SwitchNode.prototype.render = function(buf, contexts, errors) {
      var caseNode, value;
      value = this.id.resolve(contexts, errors);
      if (!(value != null)) {
        Handlebar.renderError(errors, id, " didn't resolve to any value");
        return;
      }
      if (typeof value !== 'string') {
        Handlebar.renderError(errors, id, " didn't resolve to a String, instead " + typeof value);
        return;
      }
      caseNode = this._cases[value];
      if (caseNode != null) return caseNode.render(buf, contexts, errors);
    };

    return SwitchNode;

  })();

  CaseNode = (function() {

    function CaseNode(children) {
      this.children = children;
    }

    CaseNode.prototype.render = function(buf, contexts, errors) {
      var child, _i, _len, _ref, _results;
      _ref = this.children;
      _results = [];
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        child = _ref[_i];
        _results.push(child.render(buf, contexts, errors));
      }
      return _results;
    };

    return CaseNode;

  })();

  Token = (function() {

    function Token(name, text, clazz) {
      this.name = name;
      this.text = text;
      this.clazz = clazz;
    }

    return Token;

  })();

  Token.values = [new Token("OPEN_START_SECTION", "{{#", SectionNode), new Token("OPEN_START_VERTED_SECTION", "{{?", VertedSectionNode), new Token("OPEN_START_INVERTED_SECTION", "{{^", InvertedSectionNode), new Token("OPEN_START_JSON", "{{*", JsonNode), new Token("OPEN_START_PARTIAL", "{{+", PartialNode), new Token("OPEN_START_SWITCH", "{{:", SwitchNode), new Token("OPEN_CASE", "{{=", CaseNode), new Token("OPEN_END_SECTION", "{{/", null), new Token("OPEN_UNESCAPED_VARIABLE", "{{{", UnescapedVariableNode), new Token("CLOSE_MUSTACHE3", "}}}", null), new Token("OPEN_VARIABLE", "{{", EscapedVariableNode), new Token("CLOSE_MUSTACHE", "}}", null), new Token("CHARACTER", ".", StringNode)];

  (function() {
    var token, _i, _len, _ref, _results;
    _ref = Token.values;
    _results = [];
    for (_i = 0, _len = _ref.length; _i < _len; _i++) {
      token = _ref[_i];
      _results.push(Token[token.name] = token);
    }
    return _results;
  })();

  TokenStream = (function() {

    function TokenStream(string) {
      this.nextToken = null;
      this._remainder = string;
      this._nextContents = null;
      this.advance();
    }

    TokenStream.prototype.hasNext = function() {
      return this.nextToken != null;
    };

    TokenStream.prototype.advanceOver = function(token) {
      if (this.nextToken !== token) {
        throw new ParseException("Expecting token " + token.name + " but got " + this.nextToken.name);
      }
      return this.advance();
    };

    TokenStream.prototype.advance = function() {
      var token, _i, _len, _ref;
      this.nextToken = null;
      this._nextContents = null;
      if (this._remainder.length === 0) return null;
      _ref = Token.values;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        token = _ref[_i];
        if (this._remainder.slice(0, token.text.length) === token.text) {
          this.nextToken = token;
          break;
        }
      }
      if (this.nextToken === null) this.nextToken = Token.CHARACTER;
      this._nextContents = this._remainder.slice(0, this.nextToken.text.length);
      this._remainder = this._remainder.slice(this.nextToken.text.length);
      return this.nextToken;
    };

    TokenStream.prototype.nextString = function() {
      var buf;
      buf = new StringBuilder();
      while (this.nextToken === Token.CHARACTER) {
        buf.append(this._nextContents);
        this.advance();
      }
      return buf.toString();
    };

    return TokenStream;

  })();

  createIdentifier = function(path) {
    if (path === '@') {
      return new ThisIdentifier();
    } else {
      return new PathIdentifier(path);
    }
  };

  Handlebar = (function() {

    function Handlebar(template) {
      this.nodes = [];
      this._parseTemplate(template, this.nodes);
    }

    Handlebar.prototype._parseTemplate = function(template, nodes) {
      var tokens;
      tokens = new TokenStream(template);
      this._parseSection(tokens, nodes);
      if (tokens.hasNext()) {
        throw new ParseException("There are still tokens remaining, was there an end-section without a start-section?");
      }
    };

    Handlebar.prototype._parseSection = function(tokens, nodes) {
      var caseChildren, caseValue, children, id, node, sectionEnded, switchNode, token, _results;
      sectionEnded = false;
      _results = [];
      while (tokens.hasNext() && !sectionEnded) {
        switch (tokens.nextToken) {
          case Token.CHARACTER:
            _results.push(nodes.push(new StringNode(tokens.nextString())));
            break;
          case Token.OPEN_VARIABLE:
          case Token.OPEN_UNESCAPED_VARIABLE:
          case Token.OPEN_START_JSON:
          case Token.OPEN_START_PARTIAL:
            token = tokens.nextToken;
            id = this._openSection(tokens);
            try {
              node = new token.clazz();
              node.init(id);
              _results.push(nodes.push(node));
            } catch (e) {
              console.warn(e);
              throw new ParseException(e.message);
            }
            break;
          case Token.OPEN_START_SECTION:
          case Token.OPEN_START_VERTED_SECTION:
          case Token.OPEN_START_INVERTED_SECTION:
            token = tokens.nextToken;
            id = this._openSection(tokens);
            children = [];
            this._parseSection(tokens, children);
            this._closeSection(tokens, id);
            try {
              node = new token.clazz();
              node.init(id, children);
              _results.push(nodes.push(node));
            } catch (e) {
              console.warn(e);
              throw new ParseException(e.message);
            }
            break;
          case Token.OPEN_START_SWITCH:
            id = this._openSection(tokens);
            while (tokens.nextToken === Token.CHARACTER) {
              tokens.advanceOver(Token.CHARACTER);
            }
            switchNode = new SwitchNode(id);
            nodes.push(switchNode);
            while (tokens.hasNext() && tokens.nextToken === Token.OPEN_CASE) {
              tokens.advanceOver(Token.OPEN_CASE);
              caseValue = tokens.nextString();
              tokens.advanceOver(Token.CLOSE_MUSTACHE);
              caseChildren = [];
              this._parseSection(tokens, caseChildren);
              switchNode.addCase(caseValue, new CaseNode(caseChildren));
            }
            _results.push(this._closeSection(tokens, id));
            break;
          case Token.OPEN_CASE:
            _results.push(sectionEnded = true);
            break;
          case Token.OPEN_END_SECTION:
            _results.push(sectionEnded = true);
            break;
          case Token.CLOSE_MUSTACHE:
            throw new ParseException("Orphaned " + tokens.nextToken);
            break;
          default:
            _results.push(void 0);
        }
      }
      return _results;
    };

    Handlebar.prototype._openSection = function(tokens) {
      var id, openToken;
      openToken = tokens.nextToken;
      tokens.advance();
      id = createIdentifier(tokens.nextString());
      if (openToken === Token.OPEN_UNESCAPED_VARIABLE) {
        tokens.advanceOver(Token.CLOSE_MUSTACHE3);
      } else {
        tokens.advanceOver(Token.CLOSE_MUSTACHE);
      }
      return id;
    };

    Handlebar.prototype._closeSection = function(tokens, id) {
      var nextString;
      tokens.advanceOver(Token.OPEN_END_SECTION);
      nextString = tokens.nextString();
      if (nextString.length > 0 && id.toString() !== nextString) {
        throw new ParseException("Start section " + id + " doesn't match end section " + nextString);
      }
      return tokens.advanceOver(Token.CLOSE_MUSTACHE);
    };

    Handlebar.prototype.render = function() {
      var buf, context, contextDeque, contexts, errors, _i, _len;
      contexts = 1 <= arguments.length ? __slice.call(arguments, 0) : [];
      contextDeque = [];
      for (_i = 0, _len = contexts.length; _i < _len; _i++) {
        context = contexts[_i];
        contextDeque.push(context);
      }
      buf = new StringBuilder();
      errors = [];
      Handlebar.renderNodes(buf, this.nodes, contextDeque, errors);
      return new RenderResult(buf.toString(), errors);
    };

    return Handlebar;

  })();

  Handlebar.renderNodes = function(buf, nodes, contexts, errors) {
    var node, _i, _len, _results;
    _results = [];
    for (_i = 0, _len = nodes.length; _i < _len; _i++) {
      node = nodes[_i];
      _results.push(node.render(buf, contexts, errors));
    }
    return _results;
  };

  Handlebar.renderError = function() {
    var buf, errors, message, messages, _i, _len;
    errors = arguments[0], messages = 2 <= arguments.length ? __slice.call(arguments, 1) : [];
    if (!(errors != null)) return;
    buf = new StringBuilder();
    for (_i = 0, _len = messages.length; _i < _len; _i++) {
      message = messages[_i];
      buf.append(message);
    }
    return errors.push(buf.toString());
  };

  Handlebar.RenderResult = RenderResult;

  exports["class"] = Handlebar;

}).call(this);
