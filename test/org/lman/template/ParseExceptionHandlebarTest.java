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

import static org.junit.Assert.fail;

import org.junit.Test;

public class ParseExceptionHandlebarTest {

  @Test
  public void invalidName() {
    expectParseException("hello {{bad name}}");
    expectParseException("hello {{bad#name}}");
    expectParseException("hello {{bad-name}}");
    expectParseException("hello {{ badname}}");
    expectParseException("hello {{badname }}");
    expectParseException("hello {{badname\n}}");
  }

  @Test
  public void preterminatedSection() {
    expectParseException("hello {{/flats1}}");
  }

  @Test
  public void unterminatedSection() {
    expectParseException("hello {{#flats1}}blah");
  }

  @Test
  public void unmatchedSection() {
    expectParseException("hello {{#flats1}}blah{{/flats2}}");
  }

  private static void expectParseException(String template) {
    try {
      new Handlebar(template);
      fail();
    } catch (Handlebar.ParseException expected) {}
  }
}
