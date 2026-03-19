/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

public final class Utils {

  /** com/foo/Bar to com.foo.Bar */
  public static String getClassName(String internalName) {
    return internalName.replace('/', '.');
  }

  /** com.foo.Bar to com/foo/Bar */
  public static String getInternalName(Class<?> clazz) {
    return clazz.getName().replace('.', '/');
  }

  /** com.foo.Bar to com/foo/Bar.class */
  public static String getResourceName(String className) {
    return className.replace('.', '/') + ".class";
  }

  /**
   * Get class bytecode as InputStream. Caller is responsible for closing the stream.
   *
   * @param classLoader class loader
   * @param className class fully qualified name, e.g. com.foo.Bar
   * @return input stream of class file content
   * @throws IOException if unable to read the class file
   */
  public static InputStream getClassFileStream(ClassLoader classLoader, String className)
      throws IOException {
    return getResourceStream(classLoader, getResourceName(className));
  }

  /**
   * Get resource as InputStream. Caller is responsible for closing the stream.
   *
   * @param classLoader class loader
   * @param resource resource fully qualified path, e.g. com/foo/Bar.class
   * @return input stream of resource file content
   */
  public static InputStream getResourceStream(ClassLoader classLoader, String resource)
      throws IOException {
    URLConnection connection =
        Preconditions.checkNotNull(
                classLoader.getResource(resource), "Couldn't find resource %s", resource)
            .openConnection();

    // Since the JarFile cache is not per class loader, but global with path as key, using cache may
    // cause the same instance of JarFile being used for consecutive builds, even if the file has
    // been changed. There is still another cache in ZipFile.Source which checks last modified time
    // as well, so the zip index is not scanned again on every class.
    connection.setUseCaches(false);
    return connection.getInputStream();
  }

  private Utils() {}
}
