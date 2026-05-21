/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.logging.Level.FINE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class EmbeddedInstrumentationProperties {

  private static final Logger logger =
      Logger.getLogger(EmbeddedInstrumentationProperties.class.getName());

  private static final ClassLoader DEFAULT_LOADER;

  static {
    ClassLoader defaultLoader = EmbeddedInstrumentationProperties.class.getClassLoader();
    if (defaultLoader == null) {
      defaultLoader = new BootstrapProxy();
    }
    DEFAULT_LOADER = defaultLoader;
  }

  private static volatile ClassLoader loader = DEFAULT_LOADER;
  private static final Map<String, String> versions = new ConcurrentHashMap<>();

  public static void setPropertiesLoader(ClassLoader propertiesLoader) {
    if (loader != DEFAULT_LOADER) {
      logger.warning(
          "Embedded properties loader has already been set up, further setPropertiesLoader() calls are ignored");
      return;
    }
    loader = propertiesLoader;
  }

  @Nullable
  public static String findVersion(String instrumentationName) {
    return versions.computeIfAbsent(
        instrumentationName, EmbeddedInstrumentationProperties::loadVersion);
  }

  @Nullable
  private static String loadVersion(String instrumentationName) {
    String version = loadVersionFromClass(instrumentationName);
    if (version == null) {
      version = loadVersionFromProperties(instrumentationName);
    }
    return version;
  }

  private static final Pattern STRIP_VERSION_SUFFIX = Pattern.compile("(-[0-9.]*)$");
  private static final Pattern NORMALIZE_VERSION = Pattern.compile("([0-9]+)\\.([0-9]+)");
  private static final Pattern EXTRACT_VERSION = Pattern.compile(".*?([0-9.]*)$");

  // visible for testing
  @Nullable
  static String loadVersionFromClass(String instrumentationName) {
    // The same logic is duplicated in otel.instrumentation-version
    // Strip trailing version suffix and remove dashes
    String moduleName =
        STRIP_VERSION_SUFFIX.matcher(instrumentationName).replaceAll("").replace("-", "");
    // If the module name contains a non-trailing version number e.g. jaxrs-3.0-jersey-3.0 replace
    // the dot with underscore. This is needed to turn the module name into valid package name, java
    // package name segments cannot start with a number.
    moduleName = NORMALIZE_VERSION.matcher(moduleName).replaceAll("$1_$2");
    // Extract trailing version number and replace dots with underscores so it could be used as a
    // package name segment.
    String baseVersion =
        EXTRACT_VERSION.matcher(instrumentationName).replaceAll("$1").replace(".", "_");
    String packageName = moduleName + (baseVersion.isEmpty() ? "" : ".v" + baseVersion);

    try {
      Class<?> clazz =
          Class.forName(packageName + ".internal.InstrumentationVersion", false, loader);
      return clazz.getField("VERSION").get(null).toString();
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  // visible for testing
  @Nullable
  static String loadVersionFromProperties(String instrumentationName) {
    String path =
        "META-INF/io/opentelemetry/instrumentation/" + instrumentationName + ".properties";
    try (InputStream in = loader.getResourceAsStream(path)) {
      if (in == null) {
        logger.log(FINE, "Did not find embedded instrumentation properties file {0}", path);
        return null;
      }
      Properties parsed = new Properties();
      parsed.load(in);
      return parsed.getProperty("version");
    } catch (IOException e) {
      logger.log(FINE, "Failed to load embedded instrumentation properties file " + path, e);
      return null;
    }
  }

  private static final class BootstrapProxy extends ClassLoader {
    BootstrapProxy() {
      super(null);
    }
  }

  private EmbeddedInstrumentationProperties() {}
}
