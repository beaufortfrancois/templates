# Motemplate templates

Motemplate is a mostly-logicless data-binding templating language, initially inspired by [mustache](http://mustache.github.com/) and [ctemplate](http://code.google.com/p/ctemplate) but with stronger data-binding and logic primitives.

The goal of Motemplate is to provide high quality templates across the whole stack of a web application. Write templates once, statically render content on the Java/Python/node server, then as content is added, dynamically render it using the same templates on the JavaScript client. This means:

* Minimal logic (not logic-*less* but pretty dumb).
* Powerful data-binding primitives.

The reference implementation of Motemplate is written in Python. The other platforms (Java and JavaScript) lag quite far behind - not only are they missing newer features, but they haven't even been renamed to Motemplate yet.

## Overview

A template is text plus `{{`/`}}`-style markup, for example,

    <h1>Party shopping list</h1>
    <ul>
    {{#guest:guests}}
      <li>{{guest.name}} - {{guest.favorites.food}}
      {{?guest.favourites.color}}
      <li>{{guest.name}} - {{guest.favorites.color}}
      {{/guest.favourites.color}}
    {{/guests}}
    </ul>

    ...

    {
      "guests": [{
        "name": "Ben",
        "favorites": {
          "food": "Spaghetti",
          "color": "Blue"
        }
      },{
        "name": "Fred",
        "favorites": {
          "food": "Penne"
        }
      }]
    }

A template is rendered by passing it object data. The types vary per implementation:

* Python uses any object which declares a `get()` method - such as dicts.
* Java reflects over properties and maps.
* JavaScript uses objects.

## The End

Unfortunately, that's all the documentation for now. I haven't had time to keep it up to date with the development needed to support [Chromium's Docserver](https://chromium.googlesource.com/chromium/src.git/+/master/chrome/common/extensions/docs/), so I've deleted it, but you can attempt to glance at the templates in Chromium (especially [these](https://chromium.googlesource.com/chromium/src.git/+/master/chrome/common/extensions/docs/templates/private/)) and the test cases in this repository.
