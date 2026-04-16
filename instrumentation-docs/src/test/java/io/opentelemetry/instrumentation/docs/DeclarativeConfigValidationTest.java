/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.fail;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Validates that declarative_name values in metadata.yaml files correctly map to their
 * corresponding flat property names using the actual
 * ConfigPropertiesBackedDeclarativeConfigProperties bridge.
 *
 * <p>For each configuration with a declarative_name, this test: 1. Creates a ConfigProperties with
 * the flat property (name) set to a test value 2. Navigates to the declarative path
 * (declarative_name) using the bridge 3. Verifies that the value can be retrieved at that path
 */
@SuppressWarnings("NullAway")
class DeclarativeConfigValidationTest {

  private static final String TEST_VALUE = "test-validation-value";
  private static final Path INSTRUMENTATION_DIR = Paths.get("../instrumentation");

  @Test
  void validateDeclarativeNames() throws IOException {
    List<ValidationResult> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    // Find all metadata.yaml files
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
    System.out.printf(
        "Validated %d declarative names: %d valid, %d invalid%n",
        results.size(), validCount, results.size() - validCount);

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

    // Create a ConfigProperties with the flat property set
    Map<String, String> properties = new HashMap<>();
    properties.put(flatProperty, TEST_VALUE);
    DefaultConfigProperties configProperties = DefaultConfigProperties.createFromMap(properties);

    // Create the bridge
    DeclarativeConfigProperties declarativeConfig =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            configProperties);

    // Navigate to the declarative path and try to get the value
    String retrievedValue = navigateAndGetValue(declarativeConfig, declarativePath);

    boolean valid = TEST_VALUE.equals(retrievedValue);

    return new ValidationResult(
        metadataFile.toString(), flatProperty, declarativePath, valid, retrievedValue);
  }

  /**
   * Navigates through the declarative config using the path segments and retrieves the value.
   *
   * <p>The path format is like "java.grpc.emit_message_events" or
   * "java.logback_appender.capture_code_attributes/development".
   */
  private static String navigateAndGetValue(DeclarativeConfigProperties config, String path) {
    String[] segments = path.split("\\.");

    DeclarativeConfigProperties current = config;

    // Navigate through all segments except the last one
    for (int i = 0; i < segments.length - 1; i++) {
      current = current.getStructured(segments[i]);
      if (current == null) {
        return null;
      }
    }

    // Get the value from the last segment
    String lastSegment = segments[segments.length - 1];
    return current.getString(lastSegment);
  }

  private record ValidationResult(
      String metadataFile,
      String flatProperty,
      String declarativePath,
      boolean valid,
      String retrievedValue) {

    @Override
    public String toString() {
      if (valid) {
        return String.format("  OK: %s -> %s", flatProperty, declarativePath);
      } else {
        return String.format(
            "  FAIL in %s:%n"
                + "    flat property: %s%n"
                + "    declarative_name: %s%n"
                + "    expected: %s%n"
                + "    got: %s",
            metadataFile, flatProperty, declarativePath, TEST_VALUE, retrievedValue);
      }
    }
  }
}
