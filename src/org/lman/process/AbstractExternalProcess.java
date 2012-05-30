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

package org.lman.process;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractExternalProcess {

  public static class Output {
    public final int exitCode;
    public final String stdout;
    public final String stderr;

    private Output(int exitCode, String stdout, String stderr) {
      this.exitCode = exitCode;
      this.stdout = stdout;
      this.stderr = stderr;
    }

    @Override
    public String toString() {
      return new StringBuilder()
          .append("======\n")
          .append("STDOUT\n")
          .append(stdout).append(stdout.endsWith("\n") ? "" : "$")
          .append("======\n")
          .append("STDERR\n")
          .append(stderr).append(stderr.endsWith("\n") ? "" : "$")
          .append("======\n")
          .toString();
    }
  }

  private final File directory;
  private final String moduleName;

  public AbstractExternalProcess(File directory, String moduleName) {
    if (!directory.isDirectory())
      throw new IllegalArgumentException(directory + " is not a directory");
    this.directory = directory;
    this.moduleName = moduleName;
  }

  protected abstract String getRuntimePath();

  protected abstract String[] getEnvironment();

  /**
   * Run the process and return its output.
   */
  public Output run(String... args) throws IOException {
    List<String> cmd = new ArrayList<String>();
    cmd.add(getRuntimePath());
    cmd.add(moduleName);
    cmd.addAll(Arrays.asList(args));
    Process p = Runtime.getRuntime().exec(
        cmd.toArray(new String[]{}),
        getEnvironment(),
        directory);
    String stdout = getStreamAsString(p.getInputStream());
    String stderr = getStreamAsString(p.getErrorStream());
    int exitCode = -1;
    try {
      exitCode = p.waitFor();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return new Output(exitCode, stdout, stderr);
  }

  private static String getStreamAsString(InputStream in) throws IOException {
    StringBuilder result = new StringBuilder();
    Reader reader = new InputStreamReader(in, "UTF-8");
    int c;
    while ((c = reader.read()) != -1)
      result.append((char) c);
    return result.toString();
  }

}
