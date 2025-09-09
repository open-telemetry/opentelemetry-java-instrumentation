/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstrumentationMetadataDeserializerTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper(new YAMLFactory());
    SimpleModule module = new SimpleModule();
    module.addDeserializer(
        InstrumentationMetadata.class, new InstrumentationMetadataDeserializer());
    objectMapper.registerModule(module);
  }

  @Test
  void testSuccessfulDeserialization() throws IOException {
    String yaml =
        """
        description: Test instrumentation for HTTP clients
        classification:
          - library
          - enricher
        disabled_by_default: true
        configurations:
        - name: otel.instrumentation.messaging.experimental.receive-telemetry.enabled
          description: >
            Enables experimental receive telemetry, which will cause consumers to start a new trace, with
            only a span link connecting it to the producer trace.
          type: boolean
          default: false
        - name: otel.instrumentation.messaging.experimental.capture-headers
          description: Allows configuring headers to capture as span attributes.
          type: list
          default: ''
        """;

    InstrumentationMetadata metadata = objectMapper.readValue(yaml, InstrumentationMetadata.class);

    assertThat(metadata.getDescription()).isEqualTo("Test instrumentation for HTTP clients");
    assertThat(metadata.getDisabledByDefault()).isTrue();
    assertThat(metadata.getClassifications())
        .containsExactly(
            InstrumentationClassification.LIBRARY, InstrumentationClassification.ENRICHER);

    List<ConfigurationOption> configurations = metadata.getConfigurations();
    assertThat(configurations).hasSize(2);

    ConfigurationOption firstConfig = configurations.get(0);
    assertThat(firstConfig.name())
        .isEqualTo("otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
    assertThat(firstConfig.description())
        .isEqualTo(
            "Enables experimental receive telemetry, which will cause consumers to start a new trace, with only a span link connecting it to the producer trace.\n");
    assertThat(firstConfig.type()).isEqualTo(ConfigurationType.BOOLEAN);
    assertThat(firstConfig.defaultValue()).isEqualTo("false");

    ConfigurationOption secondConfig = configurations.get(1);
    assertThat(secondConfig.name())
        .isEqualTo("otel.instrumentation.messaging.experimental.capture-headers");
    assertThat(secondConfig.description())
        .isEqualTo("Allows configuring headers to capture as span attributes.");
    assertThat(secondConfig.type()).isEqualTo(ConfigurationType.LIST);
    assertThat(secondConfig.defaultValue()).isEqualTo("");
  }

  @Test
  void testMinimalValidYaml() throws IOException {
    String yaml = "description: Simple test";

    InstrumentationMetadata metadata = objectMapper.readValue(yaml, InstrumentationMetadata.class);

    assertThat(metadata.getDescription()).isEqualTo("Simple test");
    assertThat(metadata.getDisabledByDefault()).isFalse();
    assertThat(metadata.getClassifications())
        .containsExactly(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testEmptyYaml() throws IOException {
    String yaml = "{}";

    InstrumentationMetadata metadata = objectMapper.readValue(yaml, InstrumentationMetadata.class);

    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isFalse();
    assertThat(metadata.getClassifications())
        .containsExactly(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testSingleClassificationAsArray() throws IOException {
    String yaml =
        """
        classification:
          - internal
        """;

    InstrumentationMetadata metadata = objectMapper.readValue(yaml, InstrumentationMetadata.class);

    assertThat(metadata.getClassifications())
        .containsExactly(InstrumentationClassification.INTERNAL);
  }

  @Test
  void testClassificationAsArray() throws IOException {
    String yaml =
        """
        classification:
          - custom
          - enricher
        """;

    InstrumentationMetadata metadata = objectMapper.readValue(yaml, InstrumentationMetadata.class);

    assertThat(metadata.getClassifications())
        .containsExactly(
            InstrumentationClassification.CUSTOM, InstrumentationClassification.ENRICHER);
  }

  @Test
  void testAllConfigurationTypes() throws IOException {
    String yaml =
        """
        configurations:
          - name: boolean.config
            description: Boolean configuration
            type: boolean
            default: true
          - name: string.config
            description: String configuration
            type: string
            default: default-value
          - name: int.config
            description: Integer configuration
            type: int
            default: 42
          - name: map.config
            description: Map configuration
            type: map
            default: "{}"
          - name: list.config
            description: List configuration
            type: list
            default: "[]"
        """;

    InstrumentationMetadata metadata = objectMapper.readValue(yaml, InstrumentationMetadata.class);

    List<ConfigurationOption> configurations = metadata.getConfigurations();
    assertThat(configurations).hasSize(5);

    assertThat(configurations.get(0).type()).isEqualTo(ConfigurationType.BOOLEAN);
    assertThat(configurations.get(1).type()).isEqualTo(ConfigurationType.STRING);
    assertThat(configurations.get(2).type()).isEqualTo(ConfigurationType.INT);
    assertThat(configurations.get(3).type()).isEqualTo(ConfigurationType.MAP);
    assertThat(configurations.get(4).type()).isEqualTo(ConfigurationType.LIST);
  }

  @Test
  void testEmptyConfigurationsArray() throws IOException {
    String yaml = "configurations: []";

    InstrumentationMetadata metadata = objectMapper.readValue(yaml, InstrumentationMetadata.class);

    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testConfigurationMissingName() {
    String yaml =
        """
        configurations:
          - description: Missing name field
            type: boolean
            default: true
        """;

    assertThatThrownBy(() -> objectMapper.readValue(yaml, InstrumentationMetadata.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configuration entry is missing required 'name' field");
  }

  @Test
  void testConfigurationMissingDescription() {
    String yaml =
        """
        configurations:
          - name: test.config
            type: boolean
            default: true
        """;

    assertThatThrownBy(() -> objectMapper.readValue(yaml, InstrumentationMetadata.class))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Configuration 'test.config' is missing required 'description' field");
  }

  @Test
  void testConfigurationMissingDefault() {
    String yaml =
        """
        configurations:
          - name: test.config
            description: Test configuration
            type: boolean
        """;

    assertThatThrownBy(() -> objectMapper.readValue(yaml, InstrumentationMetadata.class))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Configuration 'test.config' is missing required 'default' field");
  }

  @Test
  void testConfigurationMissingType() {
    String yaml =
        """
        configurations:
          - name: test.config
            description: Test configuration
            default: true
        """;

    assertThatThrownBy(() -> objectMapper.readValue(yaml, InstrumentationMetadata.class))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Configuration 'test.config' is missing required 'type' field");
  }

  @Test
  void testConfigurationInvalidType() {
    String yaml =
        """
        configurations:
          - name: test.config
            description: Test configuration
            type: invalid_type
            default: true
        """;

    assertThatThrownBy(() -> objectMapper.readValue(yaml, InstrumentationMetadata.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Configuration 'test.config' has invalid type: 'invalid_type'");
  }

  @Test
  void testClassificationAsStringShouldFail() {
    String yaml = "classification: internal";

    assertThatThrownBy(() -> objectMapper.readValue(yaml, InstrumentationMetadata.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Classification must be an array");
  }

  @Test
  void testInvalidClassification() {
    String yaml =
        """
        classification:
          - invalid_classification
        """;

    assertThatThrownBy(
            () -> {
              InstrumentationMetadata metadata =
                  objectMapper.readValue(yaml, InstrumentationMetadata.class);
              metadata.getClassifications(); // This triggers the validation
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid classification: invalid_classification");
  }

  @Test
  void testMixedValidAndInvalidClassifications() {
    String yaml =
        """
        classification:
          - library
          - invalid_classification
          - internal
        """;

    assertThatThrownBy(
            () -> {
              InstrumentationMetadata metadata =
                  objectMapper.readValue(yaml, InstrumentationMetadata.class);
              metadata.getClassifications(); // This triggers the validation
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid classification: invalid_classification");
  }

  @Test
  void testEmptyClassificationArrayDefaultsToLibrary() throws IOException {
    String yaml = "classification: []";

    InstrumentationMetadata metadata = objectMapper.readValue(yaml, InstrumentationMetadata.class);

    assertThat(metadata.getClassifications())
        .containsExactly(InstrumentationClassification.LIBRARY);
  }
}
