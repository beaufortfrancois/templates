# Handlebar templates

Handlebar is a mostly-logicless data-binding templating language, initially inspired by [mustache](http://mustache.github.com/) and [ctemplate](http://code.google.com/p/ctemplate) but with stronger data-binding and logic primitives. It has nothing to do with [handlebars](http://http://handlebarsjs.com/), sorry about that.

The goal of Handlebar is to provide high quality templates across the whole stack of a web application. Write templates once, statically render content on the Java/Python/node server, then as content is added, dynamically render it using the same templates on the JavaScript client. This means:
* Minimal logic (not logic-*less* but pretty dumb).
* Powerful data-binding primitives.

The original and reference implementation of Handlebar is written in Java, with a JavaScript (via CoffeeScript) and Python port. The tests are cross-platform but controlled from Java with JUnit; this means that all implementations are hopefully up to date (or else some tests are failing).

## Overview

A template is text plus `{{`/`}}`-style markup, for example,

    <h1>Party shopping list</h1>
    <ul>
    {{#guests}}
      <li>{{name}} - {{favorites.food}}
      {{?favourites.color}}
      <li>{{name}} - {{favorites.color}}
      {{/favourites.color}}
    {{/guests}}
    </ul>

    ...

    { "guests": [
      { "name": "Ben",
        "favorites": { "food": "Spaghetti",
                       "color": "Blue" },
      },
      { "name": "Fred",
         "favorites": { "food": "Penne" }
      }
    ] }

A template is rendered by passing it JSON data. I say "JSON" but what I really mean is JSON-like data; the actual input will vary depending on the language bindings (Java uses reflection over properties and maps, JavaScript uses objects, Python uses dictionaries).

Generally speaking there are two classes of markup tags: "single" ones like `{{foo}}` `{{{foo}}}` `{{*foo}}`, and "block" ones like `{{#foo}}...{{/foo}}` `{{?foo}}...{{/foo}}` `{{^foo}}...{{/foo}}` where the `...` is arbitrary other template data.

In both cases the `foo` represents a path into the JSON structure, so

    {{foo.bar.baz}}

is valid for JSON like

    {"foo": {"bar": {"baz": 42 }}}

(here it would resolve to `42`).

All libraries also have the behaviour where descending into a section of the JSON will "push" the sub-JSON onto the top of the "context stack", so given JSON like

    {"foo": {"bar": {"foo": 42 }}}

the template

    {{foo.bar.foo}} {{?foo}}{{bar.foo}} {{?bar}{{foo}}{{/bar}} {{/foo}}

will correctly resolve all references to be `42`.

There is an additional identifier `@` representing the "tip" of that context stack, useful when iterating using the `{{#foo}}...{{/foo}}` structure; `{ "list": [1,2,3] }` with `{{#list}} {{@}} {{/list}}` will print ` 1  2  3 `.

Finally, note that the `{{/foo}}` in `{{#foo}}...{{/foo}}` is actually redundant, and that `{{#foo}}...{{/}}` would be sufficient. Depdening on the situation one or the other will tend to be more readable (explicitly using `{{/foo}}` will perform more validation). A pattern I use is for multi-line blocks to have the name of the tag, single-line blocks to leave it out.

## Tags

### `{{foo.bar}}`

Prints out the HTML-escaped value at path `foo.bar`.

### `{{{foo.bar}}}`

Prints out value at path `foo.bar` (no escaping).

### `{{*foo.bar}}}`

Prints out the JSON serialization of the object at path `foo.bar` (no escaping; this is designed for JavaScript client bootstrapping).

### `{{+foo.bar arg1:value1 arg2:value2}}`

Inserts the sub-template (aka "partial template") found at path `foo.bar`. Currently, all libraries actually enforce that this is a pre-compiled template (rather than a plain string for example) for efficiency. This lets you do something like:

    template = Handlebar('{{#list}} {{+partial}} {{/}}')
    partial = Handlebar('{{foo}}...')
    json = {
      'list': [
        { 'foo': 42 },
        { 'foo': 56 },
        { 'foo': 10 }
      ]
    }
    print(template.render(json, {'partial': partial}).text)
    >  42...  56...  10...

Very useful for dynamic web apps, and also just very useful.

### `{{#foo.bar}}...{{/}}`

Runs `...` for each item in an array found at path `foo.bar`, or each key/value pair in an object.

### `{{?foo.bar}}...{{:}}...{{/}}`

Runs `...` if `foo.bar` resolves to a "value", which is defined based on types.

* `null` is never considered a value.
* `true` is a valid, `false` isn't.
* Any number other than `0` is a value.
* Any non-empty string is a value.
* Any non-empty array is a value.
* Any non-empty object is a value.

### `{{^foo.bar}}...{{:}}...{{/}}`

Runs `...` if `foo.bar` _doesn't_ resolve to a "value". The exact opposite of `{{?foo.bar}}...{{/}}`.

### {{- comment -}}

## Layout

Handlebar has some layout rules that should be kept in mind while writing templates.

* Blocks
* Inline
* Indentation
