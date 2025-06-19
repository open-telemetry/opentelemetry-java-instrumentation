/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

class EmbeddedConfigFile {

  private EmbeddedConfigFile() {
    // Utility class
  }

  private static final Pattern PATTERN =
      Pattern.compile(
          "^Config resource 'class path resource \\[(.+)]' via location 'optional:classpath:/'$");

  static OpenTelemetryConfigurationModel extractModel(ConfigurableEnvironment environment)
      throws IOException {
    for (PropertySource<?> propertySource : environment.getPropertySources()) {
      if (propertySource instanceof OriginTrackedMapPropertySource) {
        return getModel(environment, (OriginTrackedMapPropertySource) propertySource);
      }
    }
    throw new IllegalStateException("No application.yaml file found.");
  }

  private static OpenTelemetryConfigurationModel getModel(
      ConfigurableEnvironment environment, OriginTrackedMapPropertySource source)
      throws IOException {
    Matcher matcher = PATTERN.matcher(source.getName());
    if (matcher.matches()) {
      String file = matcher.group(1);

      try (InputStream resourceAsStream =
          environment.getClass().getClassLoader().getResourceAsStream(file)) {
        if (resourceAsStream != null) {
          return extractOtelConfigFile(resourceAsStream);
        } else {
          throw new IllegalStateException("Unable to load " + file + " in the classpath.");
        }
      }
    } else {
      throw new IllegalStateException(
          "No OpenTelemetry configuration found in the application.yaml file.");
    }
  }

  @Nullable
  @SuppressWarnings("unchecked")
  private static String parseOtelNode(InputStream in) {
    Load load = new Load(LoadSettings.builder().build());
    Dump dump = new Dump(DumpSettings.builder().build());
    for (Object o : load.loadAllFromInputStream(in)) {
      Map<String, Object> data = (Map<String, Object>) o;
      Map<String, Map<String, Object>> otel = (Map<String, Map<String, Object>>) data.get("otel");
      if (otel != null) {
        return dump.dumpToString(otel);
      }
    }
    throw new IllegalStateException("No 'otel' configuration found in the YAML file.");
  }

  private static OpenTelemetryConfigurationModel extractOtelConfigFile(InputStream content) {
    String node = parseOtelNode(content);
    if (node == null || node.isEmpty()) {
      throw new IllegalStateException("otel node is empty or null in the YAML file.");
    }

    return DeclarativeConfiguration.parse(
        new ByteArrayInputStream(node.getBytes(StandardCharsets.UTF_8)));
  }
}
