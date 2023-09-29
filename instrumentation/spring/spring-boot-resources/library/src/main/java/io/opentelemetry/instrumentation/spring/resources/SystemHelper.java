/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

class SystemHelper {
  private static final Logger logger = Logger.getLogger(SystemHelper.class.getName());

  private final ClassLoader classLoader;
  private final boolean addBootInfPrefix;

  SystemHelper() {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    classLoader =
        contextClassLoader != null ? contextClassLoader : ClassLoader.getSystemClassLoader();
    addBootInfPrefix = classLoader.getResource("BOOT-INF/classes/") != null;
    if (addBootInfPrefix) {
      logger.log(Level.FINER, "Detected presence of BOOT-INF/classes/");
    }
  }

  String getenv(String name) {
    return System.getenv(name);
  }

  String getProperty(String key) {
    return System.getProperty(key);
  }

  InputStream openClasspathResource(String filename) {
    String path = addBootInfPrefix ? "BOOT-INF/classes/" + filename : filename;
    return classLoader.getResourceAsStream(path);
  }

  InputStream openClasspathResource(String directory, String filename) {
    String path = directory + "/" + filename;
    return classLoader.getResourceAsStream(path);
  }

  InputStream openFile(String filename) throws Exception {
    return Files.newInputStream(Paths.get(filename));
  }

  /**
   * Attempts to use ProcessHandle to get the full commandline of the current process (including the
   * main method arguments). Will only succeed on java 9+.
   */
  @SuppressWarnings("unchecked")
  String[] attemptGetCommandLineArgsViaReflection() throws Exception {
    Class<?> clazz = Class.forName("java.lang.ProcessHandle");
    Method currentMethod = clazz.getDeclaredMethod("current");
    Method infoMethod = clazz.getDeclaredMethod("info");
    Object currentInstance = currentMethod.invoke(null);
    Object info = infoMethod.invoke(currentInstance);
    Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
    Method argumentsMethod = infoClass.getMethod("arguments");
    Optional<String[]> optionalArgs = (Optional<String[]>) argumentsMethod.invoke(info);
    return optionalArgs.orElse(new String[0]);
  }
}
