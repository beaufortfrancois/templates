#!/usr/bin/python
# Copyright 2013 Benjamin Kalman
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

from motemplate import Motemplate
import json
import os
import sys
import unittest

def _Read(name):
  test_data = os.path.join(
      sys.path[0], os.pardir, 'test', 'org', 'lman', 'template', 'data')
  with open(os.path.join(test_data, name), 'r') as f:
    return f.read()

class MotemplateTest(unittest.TestCase):
  def testAtInLists(self):
    self._Run('atInLists', partials=('one',), expect_errors=True)
  def testAssertions(self):
    self._Run('assertions', expect_errors=True)
  def testBlockPartials(self):
    self._Run('blockPartials', partials=('wrapper',))
  def testCleanPartials(self):
    self._Run('cleanPartials', partials=('p1', 'p2'))
  def testCleanRendering(self):
    self._Run('cleanRendering', expect_errors=True)
  def testComment(self):
    self._Run('comment')
  def testDeepNulls(self):
    self._Run('deepNulls')
  def testDeepPartials(self):
    self._Run('deepPartials', partials=('partial',))
  def testElseRendering(self):
    self._Run('elseRendering')
  def testEmptyLists(self):
    self._Run('emptyLists')
  def testEmptySections(self):
    self._Run('emptySections')
  def testErrors(self):
    self._Run('errors', expect_errors=True)
  def testEscaping(self):
    self._Run('escaping')
  def testExistence(self):
    self._Run('existence')
  def testFlat(self):
    self._Run('flat')
  def testHasPartial(self):
    self._Run('hasPartial', partials=('partial',))
  def testIdentity(self):
    self._Run('identity')
  def testInvertedSections(self):
    self._Run('invertedSections', instance='0')
    self._Run('invertedSections', instance='1')
    self._Run('invertedSections', instance='2', expect_errors=True)
    self._Run('invertedSections', instance='3')
  def testJson(self):
    self._Run('json')
  def testMulti(self):
    self._Run('multi')
  def testNonexistence(self):
    self._Run('nonexistence')
  def testNonexistentName(self):
    self._Run('nonexistentName', expect_errors=True)
  def testNullsWithQuestion(self):
    self._Run('nullsWithQuestion')
  def testNullsWithSection(self):
    self._Run('nullsWithSection', expect_errors=True)
  def testObjectSections(self):
    self._Run('objectSections')
  def testOptionalEndSectionName(self):
    self._Run('optionalEndSectionName')
  def testPartialInheritance(self):
    self._Run('partialInheritance', partials=('p1',), expect_errors=True)
  def testPartialPartials(self):
    self._Run('partialPartials', partials=('one', 'two'), expect_errors=True)
  def testPaths(self):
    self._Run('paths')
  def testSelfClosing(self):
    self._Run('selfClosing', partials=('p1',))
  def testStringPartialParams(self):
    self._Run('stringPartialParams', partials=('p1', 'p2'))
  def testThis(self):
    self._Run('this')
  def testTransitivePartials(self):
    self._Run('transitivePartials', partials=('p1',))
  def testValidIds(self):
    self._Run('validIds')
  def testPreserveStyleTag(self):
    self._Run('preserveStyleTag', preserve_style_tag=True)

  def _Run(self, name, instance=None, preserve_style_tag=False, partials=None,
           expect_errors=False):
    template = _Read('%s.template' % name)
    if instance is None:
      expected = _Read('%s.expected' % name)
      data = json.loads(_Read('%s.json' % name))
    else:
      expected = _Read('%s_%s.expected' % (name, instance))
      data = json.loads(_Read('%s_%s.json' % (name, instance)))
    if partials is not None:
      partial_data = {}
      for partial in partials:
        partial_data[partial] = Motemplate(
            _Read('%s_%s.template' % (name, partial)))
      data['partials'] = partial_data
    result = Motemplate(template, preserve_style_tag=preserve_style_tag).Render(data)
    if not expect_errors and result.errors:
      self.fail('\n'.join(result.errors))
    if expected != result.text:
      expected_lines = expected.replace('\n', '$\n').split('\n')
      actual_lines = result.text.replace('\n', '$\n').split('\n')
      max_expected_line_length = max(len(line) for line in expected_lines)
      message = []
      while expected_lines or actual_lines:
        expected_line = expected_lines.pop(0) if expected_lines else '%'
        actual_line = actual_lines.pop(0) if actual_lines else '%'
        message.append(('%s %s%s | %s' % (
            ' ' if expected_line == actual_line else '!',
            expected_line,
            ' ' * (max_expected_line_length - len(expected_line)),
            actual_line)))
      self.fail('\n' + '\n'.join(message))

if __name__ == '__main__':
  unittest.main()
