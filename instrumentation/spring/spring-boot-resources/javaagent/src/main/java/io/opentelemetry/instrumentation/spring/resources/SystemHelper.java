/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static java.util.logging.Level.FINER;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
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
      logger.log(FINER, "Detected presence of BOOT-INF/classes/");
    }
  }

  String getenv(String name) {
    return System.getenv(name);
  }

  String getProperty(String key) {
    return System.getProperty(key);
  }

  /**
   * Opens a classpath resource that lives alongside the application's classes. In Spring Boot
   * bootJar layouts the application classes live under {@code BOOT-INF/classes/}, so the prefix is
   * applied when that layout is detected.
   */
  InputStream openClasspathResource(String filename) {
    String path = addBootInfPrefix ? "BOOT-INF/classes/" + filename : filename;
    return classLoader.getResourceAsStream(path);
  }

  /**
   * Opens a classpath resource that always lives at the jar root, regardless of bootJar layout.
   * This is used for things like {@code META-INF/build-info.properties}, which Spring Boot places
   * at the jar root rather than under {@code BOOT-INF/classes/}.
   */
  InputStream openJarRootResource(String path) {
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
