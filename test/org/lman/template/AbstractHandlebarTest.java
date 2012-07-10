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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.json.JSONException;
import org.junit.Test;

/**
 * Runs all test cases found in the "data" directory.
 */
public abstract class AbstractHandlebarTest {

  private static final File DATA = new File("test/org/lman/template/data");
  private static final String TEMPLATE_EXT = ".template";
  private static final String JSON_EXT = ".json";
  private static final String EXPECTED_EXT = ".expected";

  static {
    if (!DATA.isDirectory()) {
      throw new AssertionError(DATA + " is not a directory");
    }
  }

  private class TemplateTest {
    private final File templateFile;
    private final List<File> partialTemplateFiles;
    private final File jsonFile;
    private final String expected;

    public TemplateTest(String name, String... partialTemplates) throws IOException, JSONException {
      String[] parts = name.split("_");
      String base = parts[0];
      String suffix = "";
      if (parts.length > 1)
        suffix = "_" + parts[1];

      String dataParent = DATA.getPath() + File.separator;

      this.templateFile = new File(dataParent + base + TEMPLATE_EXT);
      this.partialTemplateFiles = new ArrayList<File>(partialTemplates.length);
      for (String partialTemplate : partialTemplates) {
        this.partialTemplateFiles.add(
            new File(dataParent + partialTemplate + TEMPLATE_EXT));
      }
      this.jsonFile = new File(dataParent + base + suffix + JSON_EXT);
      this.expected = getContents(new File(dataParent + base + suffix + EXPECTED_EXT));
    }

    public void run() throws Exception {
      Assert.assertEquals(expected, render(jsonFile, templateFile, partialTemplateFiles));
    }
  }

  protected abstract String render(
      File json, File template, List<File> partialTemplates) throws Exception;

  protected static String getContents(File file) throws IOException {
    StringBuilder contents = new StringBuilder();
    BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
    int c;
    while ((c = in.read()) != -1)
      contents.append((char) c);
    return contents.toString();
  }

  private void test(String testName, String... partialTemplates) {
    try {
      new TemplateTest(testName, partialTemplates).run();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void flat() {
    test("flat");
  }

  @Test
  public void identity() {
    test("identity");
  }

  @Test
  public void this_() {
    test("this");
  }

  @Test
  public void multi() {
    test("multi");
  }

  @Test
  public void paths() {
    test("paths");
  }

  @Test
  public void objectSections() {
    test("objectSections");
  }

  @Test
  public void nonexistentName() {
    test("nonexistentName");
  }

  @Test
  public void emptyLists() {
    test("emptyLists");
  }

  @Test
  public void nullsWithSection() {
    test("nullsWithSection");
  }

  @Test
  public void nullsWithQuestion() {
    test("nullsWithQuestion");
  }

  @Test
  public void deepNulls() {
    test("deepNulls");
  }

  @Test
  public void invertedSections_0() {
    test("invertedSections_0");
  }

  @Test
  public void invertedSections_1() {
    test("invertedSections_1");
  }

  @Test
  public void invertedSections_2() {
    test("invertedSections_2");
  }

  @Test
  public void invertedSections_3() {
    test("invertedSections_3");
  }

  @Test
  public void json() {
    test("json");
  }

  @Test
  public void optionalEndSectionName() {
    test("optionalEndSectionName");
  }

  @Test
  public void escaping() {
    test("escaping");
  }

  @Test
  public void invertedStrings() {
    test("invertedStrings");
  }

  @Test
  public void vertedStrings() {
    test("vertedStrings");
  }

  @Test
  public void hasPartial() {
    test("hasPartial", "hasPartial_partial");
  }

  @Test
  public void deepPartials() {
    test("deepPartials", "deepPartials_partial");
  }

  @Test
  public void comment() {
    test("comment");
  }

  @Test
  public void cleanRendering() {
    test("cleanRendering");
  }

  @Test
  public void cleanPartials() {
    test("cleanPartials", "cleanPartials_p1");
  }

  @Test
  public void partialInheritance() {
    test("partialInheritance", "partialInheritance_p1");
  }

  @Test
  public void emptySections() {
    test("emptySections");
  }
  
  @Test
  public void elseRendering() {
    test("elseRendering");
  }

}
