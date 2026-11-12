/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class ChildFirstSystemClassLoader extends URLClassLoader {

  public ChildFirstSystemClassLoader(ClassLoader parent) {
    super(new URL[0], parent);
  }

  // The JVM calls this hook to make -javaagent JARs visible to a custom system loader.
  @SuppressWarnings("unused")
  private void appendToClassPathForInstrumentation(String path) throws MalformedURLException {
    addURL(new File(path).toURI().toURL());
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    if (name.startsWith("io.opentelemetry.javaagent.")) {
      Class<?> loaded = findLoadedClass(name);
      if (loaded == null) {
        try {
          loaded = findClass(name);
        } catch (ClassNotFoundException ignored) {
          // Delegate classes not present in the appended JARs.
        }
      }
      if (loaded != null) {
        if (resolve) {
          resolveClass(loaded);
        }
        return loaded;
      }
    }
    return super.loadClass(name, resolve);
  }
}
