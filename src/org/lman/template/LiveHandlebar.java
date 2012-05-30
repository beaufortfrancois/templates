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
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.lman.json.JsonView;

/**
 * A {@link Handlebar} which is "live", updating itself in a thread-safe way as a file changes
 * on the filesystem.
 */
// TODO: replace this with a better caching mechanism somewhere else.
public class LiveHandlebar extends Handlebar {

  private final File file;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private HandlebarImpl impl;
  private long lastRead = -1L;

  public LiveHandlebar(File file) {
    // Fill in the source when recalculating impl.
    super("");
    this.file = file;
    maybeReload();
  }

  @Override
  protected void renderInto(StringBuilder buf, Deque<JsonView> contexts, List<String> errors) {
    maybeReload();
    lock.readLock().lock();
    try {
      impl.renderInto(buf, contexts, errors);
    } finally {
      lock.readLock().unlock();
    }
  }

  private void maybeReload() {
    if (file.lastModified() <= lastRead)
      return;

    lastRead = System.currentTimeMillis();

    StringBuilder buf = new StringBuilder();
    try {
      BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
      for (int c = 0; c != -1; c = in.read()) {
        buf.append((char) c);
      }
      in.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    HandlebarImpl newImpl = new HandlebarImpl(buf.toString());

    lock.writeLock().lock();
    try {
      impl = newImpl;
      source = impl.source;
    } finally {
      lock.writeLock().unlock();
    }
  }
}
