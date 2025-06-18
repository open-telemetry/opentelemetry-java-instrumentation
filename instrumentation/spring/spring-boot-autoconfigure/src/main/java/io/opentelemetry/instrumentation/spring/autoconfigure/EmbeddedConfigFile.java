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
    String name = source.getName();
    System.out.println("Property Source: " + name); // todo remove
    Matcher matcher = PATTERN.matcher(name);
    if (matcher.matches()) {
      String file = matcher.group(1);
      System.out.println("Found application.yaml: " + file);

      try (InputStream resourceAsStream =
          environment.getClass().getClassLoader().getResourceAsStream(file)) {
        // Print the contents of the application.yaml file
        if (resourceAsStream != null) {
          //              String content = new String(resourceAsStream.readAllBytes());
          //              System.out.println("Contents of " + file + ":");  // todo remove
          //              System.out.println(content);             // todo remove

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
    //
    //	https://github.com/open-telemetry/opentelemetry-configuration/blob/c205770a956713e512eddb056570a99737e3383a/examples/kitchen-sink.yaml#L11

    // 1. read to yaml tree in jackson
    //    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    //    JsonNode rootNode = yamlMapper.readTree(content);

    String node = parseOtelNode(content);
    if (node == null || node.isEmpty()) {
      throw new IllegalStateException("otel node is empty or null in the YAML file.");
    }

    System.out.println("OpenTelemetry configuration file content:"); // todo remove
    System.out.println(node); // todo remove

    return DeclarativeConfiguration.parse(
        new ByteArrayInputStream(node.getBytes(StandardCharsets.UTF_8)));
  }
}
