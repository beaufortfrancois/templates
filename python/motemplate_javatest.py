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

import json as json_lib
import sys
from motemplate import Motemplate

""" Python counterpart to PythonHandlebarTest.
"""

argv = sys.argv
if len(argv) < 3:
  raise Exception('usage: <json> <template> partials...')

def readFile(path):
  with open(path, 'r') as f:
    return f.read()

json = json_lib.loads(readFile(argv[1]))
template = Motemplate(readFile(argv[2]))

partials = {}
for partialPath in argv[3:]:
  partialName = partialPath.split('/')[-1]
  partialName = partialName[:len(partialName) - len('.template')]
  partials[partialName] = Motemplate(readFile(partialPath))

renderResult = template.render(json, { 'partials': partials })
sys.stdout.write(renderResult.text)
sys.stderr.write('\n'.join(renderResult.errors))
