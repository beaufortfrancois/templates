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
import java.util.ArrayList;
import java.util.List;

import org.lman.process.PythonProcess;

public class PythonHandlebarTest extends AbstractHandlebarTest {

  @Override
  protected String render(File json, File template, List<File> partialTemplates) throws Exception {
    List<String> args = new ArrayList<String>();
    args.add(json.getAbsolutePath());
    args.add(template.getAbsolutePath());
    for (File partialTemplate : partialTemplates)
      args.add(partialTemplate.getAbsolutePath());

    PythonProcess node = new PythonProcess(new File("python"), "handlebar_test.py");
    PythonProcess.Output output = node.run(args.toArray(new String[]{}));
    System.out.println(output.stderr);
    return output.stdout;
  }

}
