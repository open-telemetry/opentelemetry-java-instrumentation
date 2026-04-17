/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.fail;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Validates that declarative_name values in metadata.yaml files correctly map to their
 * corresponding flat property names using the actual
 * ConfigPropertiesBackedDeclarativeConfigProperties bridge.
 */
class DeclarativeConfigValidationTest {

  private static final Logger logger =
      Logger.getLogger(DeclarativeConfigValidationTest.class.getName());

  private static final Path INSTRUMENTATION_DIR = Paths.get("../instrumentation");

  @Test
  void validateDeclarativeNames() throws IOException {
    List<ValidationResult> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(INSTRUMENTATION_DIR)) {
      List<Path> metadataFiles =
          paths.filter(p -> p.getFileName().toString().equals("metadata.yaml")).toList();

      for (Path metadataFile : metadataFiles) {
        String content = Files.readString(metadataFile);
        try {
          InstrumentationMetadata metadata = YamlHelper.metaDataParser(content);

          for (ConfigurationOption config : metadata.getConfigurations()) {
            if (config.declarativeName() != null && !config.declarativeName().isBlank()) {
              ValidationResult result = validateConfig(metadataFile, config);
              results.add(result);
              if (!result.valid) {
                errors.add(result.toString());
              }
            }
          }
        } catch (Exception e) {
          errors.add(String.format("Failed to parse %s: %s", metadataFile, e.getMessage()));
        }
      }
    }

    long validCount = results.stream().filter(r -> r.valid).count();
    logger.info(
        String.format(
            Locale.ROOT,
            "Validated %d declarative names: %d valid, %d invalid",
            results.size(),
            validCount,
            results.size() - validCount));

    if (!errors.isEmpty()) {
      fail(
          String.format(
              Locale.ROOT,
              "Found %d invalid declarative_name mappings:%n%s",
              errors.size(),
              String.join("\n", errors)));
    }
  }

  private static ValidationResult validateConfig(Path metadataFile, ConfigurationOption config) {
    String flatProperty = config.name();
    String declarativePath = config.declarativeName();
    ConfigurationType type = config.type();

    // Create test value appropriate for the type
    TestValue testValue = createTestValue(type);

    Map<String, String> properties = new HashMap<>();
    properties.put(flatProperty, testValue.propertyValue);
    DefaultConfigProperties configProperties = DefaultConfigProperties.createFromMap(properties);

    DeclarativeConfigProperties declarativeConfig =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            configProperties);

    Object retrievedValue = navigateAndGetValue(declarativeConfig, declarativePath, type);

    boolean valid = Objects.equals(testValue.expectedValue, retrievedValue);

    return new ValidationResult(
        metadataFile.toString(),
        flatProperty,
        declarativePath,
        type,
        valid,
        testValue.expectedValue,
        retrievedValue);
  }

  private static TestValue createTestValue(ConfigurationType type) {
    return switch (type) {
      case BOOLEAN -> new TestValue("true", true);
      case STRING -> new TestValue("test-validation-value", "test-validation-value");
      case INT -> new TestValue("42", 42);
      case LIST -> new TestValue("item1,item2,item3", List.of("item1", "item2", "item3"));
      case MAP -> new TestValue("key1=value1,key2=value2", "key1=value1,key2=value2");
    };
  }

  private record TestValue(String propertyValue, Object expectedValue) {}

  /**
   * Navigates through the declarative config using the path segments and retrieves the value.
   *
   * <p>The path format is like "java.grpc.emit_message_events" or
   * "java.logback_appender.capture_code_attributes/development".
   */
  private static Object navigateAndGetValue(
      DeclarativeConfigProperties config, String path, ConfigurationType type) {
    String[] segments = path.split("\\.");

    DeclarativeConfigProperties current = config;

    // Navigate through all segments except the last one
    for (int i = 0; i < segments.length - 1; i++) {
      current = current.getStructured(segments[i]);
      if (current == null) {
        return null;
      }
    }

    String lastSegment = segments[segments.length - 1];
    return switch (type) {
      case BOOLEAN -> current.getBoolean(lastSegment);
      case STRING -> current.getString(lastSegment);
      case INT -> current.getInt(lastSegment);
      case LIST -> current.getScalarList(lastSegment, String.class);
      case MAP -> current.getString(lastSegment);
    };
  }

  private record ValidationResult(
      String metadataFile,
      String flatProperty,
      String declarativePath,
      ConfigurationType type,
      boolean valid,
      Object expectedValue,
      Object retrievedValue) {

    @Override
    public String toString() {
      if (valid) {
        return String.format("  OK: %s -> %s (%s)", flatProperty, declarativePath, type);
      } else {
        return String.format(
            "  FAIL in %s:%n"
                + "    flat property: %s%n"
                + "    declarative_name: %s%n"
                + "    type: %s%n"
                + "    expected: %s%n"
                + "    got: %s",
            metadataFile, flatProperty, declarativePath, type, expectedValue, retrievedValue);
      }
    }
  }
}
