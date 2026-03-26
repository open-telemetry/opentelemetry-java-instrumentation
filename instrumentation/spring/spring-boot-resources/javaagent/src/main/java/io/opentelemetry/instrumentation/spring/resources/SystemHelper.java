/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static java.util.logging.Level.FINER;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Logger;
import javax.annotation.Nullable;

class SystemHelper {
  private static final Logger logger = Logger.getLogger(SystemHelper.class.getName());

  private final ClassLoader classLoader;
  private final boolean addBootInfPrefix;

  SystemHelper() {
    this(getContextOrSystemClassLoader());
  }

  SystemHelper(ClassLoader classLoader) {
    this.classLoader = classLoader;
    addBootInfPrefix = classLoader.getResource("BOOT-INF/classes/") != null;
    if (addBootInfPrefix) {
      logger.log(FINER, "Detected presence of BOOT-INF/classes/");
    }
  }

  private static ClassLoader getContextOrSystemClassLoader() {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    return contextClassLoader != null ? contextClassLoader : ClassLoader.getSystemClassLoader();
  }

  @Nullable
  String getenv(String name) {
    return System.getenv(name);
  }

  @Nullable
  String getProperty(String key) {
    return System.getProperty(key);
  }

  @Nullable
  InputStream openClasspathResource(String filename) {
    String path = addBootInfPrefix ? "BOOT-INF/classes/" + filename : filename;
    return classLoader.getResourceAsStream(path);
  }

  @Nullable
  InputStream openClasspathResource(String directory, String filename) {
    String path =
        addBootInfPrefix
            ? "BOOT-INF/classes/" + directory + "/" + filename
            : directory + "/" + filename;
    return classLoader.getResourceAsStream(path);
  }

  InputStream openFile(String filename) throws IOException {
    return Files.newInputStream(Paths.get(filename));
  }

  /**
   * Attempts to use ProcessHandle to get the full commandline of the current process (including the
   * main method arguments). Will only succeed on java 9+.
   */
  @SuppressWarnings("unchecked")
  String[] attemptGetCommandLineArgsViaReflection() throws ReflectiveOperationException {
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
