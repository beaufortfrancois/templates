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

// NodeJS counterpart to JsHandlebarTest.java.

var Handlebar = require('Handlebar').class;

var argv = process.argv;
if (argv.length < 4)
  throw new Error('usage: <json> <template> partials...')

function readFileSync(filename) {
  var fs = require('fs');
  return String(fs.readFileSync(filename));
}

function last(array) {
  return array[array.length - 1];
}

// argv[0..1] are "node JsHandlebarTest.js".
var jsonFile = argv[2];
var json = JSON.parse(readFileSync(jsonFile));

var templateFile = argv[3];
var template = readFileSync(templateFile);

var partialTemplates = {};
for (var i = 4; i < argv.length; i++) {
  var partialFile = argv[i];
  var name = last(partialFile.split('/'));
  name = name.substring(0, name.length - '.template'.length);
  partialTemplates[name] = new Handlebar(readFileSync(partialFile));
}

var template = new Handlebar(template);
var renderResult = template.render(json, { templates: partialTemplates });
process.stdout.write(renderResult.text);
