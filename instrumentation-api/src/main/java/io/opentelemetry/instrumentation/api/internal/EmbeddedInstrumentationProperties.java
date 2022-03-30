/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.logging.Level.FINE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class EmbeddedInstrumentationProperties {

  private static final Logger logger =
      Logger.getLogger(EmbeddedInstrumentationProperties.class.getName());

  private static final EmbeddedPropertiesLoader loader = new EmbeddedPropertiesLoader();
  private static final Map<String, String> versions = new ConcurrentHashMap<>();

  @Nullable
  public static String findVersion(String instrumentationName) {
    return versions.computeIfAbsent(
        instrumentationName, EmbeddedInstrumentationProperties::loadVersion);
  }

  @Nullable
  private static String loadVersion(String instrumentationName) {
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

  private static final class EmbeddedPropertiesLoader extends ClassLoader {

    static {
      ClassLoader.registerAsParallelCapable();
    }

    EmbeddedPropertiesLoader() {
      super(EmbeddedPropertiesLoader.class.getClassLoader());
    }

    // Do not remove this method -- it is instrumented by the javaagent so that the
    // instrumentation-api (which resides in the bootstrap CL) is able to load instrumentation
    // properties from the agent class space
    @SuppressWarnings("RedundantOverride")
    @Override
    public URL getResource(String name) {
      return super.getResource(name);
    }
  }

  private EmbeddedInstrumentationProperties() {}
}
