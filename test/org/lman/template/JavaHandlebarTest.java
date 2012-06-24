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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.lman.json.JSONObjectJsonView;
import org.lman.json.JsonView;
import org.lman.template.Handlebar.ParseException;

public class JavaHandlebarTest extends AbstractHandlebarTest {

  public static class Templates {
    public final Map<String, Handlebar> templates;

    public Templates(Map<String, Handlebar> templates) {
      this.templates = templates;
    }
  }

  @Override
  protected String render(File jsonFile, File templateFile, List<File> partialTemplateFiles)
      throws ParseException, IOException, JSONException {
    Map<String, Handlebar> partialTemplates = new HashMap<String, Handlebar>();
    for (File partialTemplateFile : partialTemplateFiles) {
      partialTemplates.put(
          getTemplateName(partialTemplateFile),
          new Handlebar(getContents(partialTemplateFile)));
    }
    return new Handlebar(getContents(templateFile)).render(
        getJson(jsonFile), new Templates(partialTemplates)).text;
  }

  private static String getTemplateName(File templateFile) {
    String name = templateFile.getName();
    return name.substring(0, name.length() - ".template".length());
  }

  private static JsonView getJson(File file) throws JSONException, IOException {
    return new JSONObjectJsonView(new JSONObject(getContents(file)));
  }

}
