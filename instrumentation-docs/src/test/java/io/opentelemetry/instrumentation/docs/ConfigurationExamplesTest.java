/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import org.junit.jupiter.api.Test;

/** Tests that the examples field in configuration options is properly parsed and serialized. */
@SuppressWarnings("NullAway")
class ConfigurationExamplesTest {

  @Test
  void testParseConfigurationWithExamples() throws Exception {
    String yaml =
        """
        description: Test instrumentation
        configurations:
          - name: otel.instrumentation.test.example-config
            declarative_name: java.test.example_config
            description: Test configuration
            type: string
            default: "default-value"
            examples:
              - "example1"
              - "example2"
              - "example3"
        """;

    InstrumentationMetadata metadata = YamlHelper.metaDataParser(yaml);

    assertThat(metadata.getConfigurations()).hasSize(1);
    assertThat(metadata.getConfigurations().get(0).name())
        .isEqualTo("otel.instrumentation.test.example-config");
    assertThat(metadata.getConfigurations().get(0).declarativeName())
        .isEqualTo("java.test.example_config");
    assertThat(metadata.getConfigurations().get(0).examples())
        .containsExactly("example1", "example2", "example3");
  }

  @Test
  void testParseConfigurationWithoutExamples() throws Exception {
    String yaml =
        """
        description: Test instrumentation
        configurations:
          - name: otel.instrumentation.test.example-config
            description: Test configuration
            type: string
            default: "default-value"
        """;

    InstrumentationMetadata metadata = YamlHelper.metaDataParser(yaml);

    assertThat(metadata.getConfigurations()).hasSize(1);
    assertThat(metadata.getConfigurations().get(0).examples()).isNull();
  }
}
