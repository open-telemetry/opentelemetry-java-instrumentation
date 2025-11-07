/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmbeddedConfigFileTest {

  @Test
  void convertFlatPropsToNested_simpleProperties() {
    Map<String, Object> flatProps = new HashMap<>();
    flatProps.put("resource.service.name", "my-service");
    flatProps.put("traces.exporter", "otlp");

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);
    assertThat(yaml).isEqualTo("");
  }

  @Test
  void convertFlatPropsToNested_booleansAndNumbersPreserveTypes() {
    Map<String, Object> flatProps = new LinkedHashMap<>();
    flatProps.put("feature.enabled", true);
    flatProps.put("limits.maxRetries", 5);
    flatProps.put("threshold", 3.14);

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    String expected =
        "feature:\n"
            + "  enabled: true\n"
            + "limits:\n"
            + "  maxRetries: 5\n"
            + "threshold: 3.14\n";

    assertThat(yaml).isEqualTo(expected);
  }

  @Test
  void convertFlatPropsToNested_arrayProperties() {
    Map<String, Object> flatProps = new HashMap<>();
    flatProps.put("instrumentation.java.list[0]", "one");
    flatProps.put("instrumentation.java.list[1]", "two");
    flatProps.put("instrumentation.java.list[2]", "three");

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);
    assertThat(yaml).isEqualTo("");
  }

  @Test
  void convertFlatPropsToNested_mixedPropertiesAndArrays() {
    Map<String, Object> flatProps = new HashMap<>();
    flatProps.put("resource.service.name", "test-service");
    flatProps.put("resource.attributes[0]", "key1=value1");
    flatProps.put("resource.attributes[1]", "key2=value2");
    flatProps.put("traces.exporter", "otlp");

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);
    assertThat(yaml).isEqualTo("");
  }

  @Test
  void convertFlatPropsToNested_emptyMap() {
    Map<String, Object> flatProps = new HashMap<>();

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);
    assertThat(yaml).isEqualTo("");

  }

  @Test
  void convertFlatPropsToNested_singleLevelProperty() {
    Map<String, Object> flatProps = new HashMap<>();
    flatProps.put("enabled", "true");

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);
    assertThat(yaml).isEqualTo("");
  }

  @Test
  void convertFlatPropsToNested_arrayWithGaps() {
    Map<String, Object> flatProps = new HashMap<>();
    flatProps.put("list[0]", "first");
    flatProps.put("list[2]", "third");

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);
    assertThat(yaml).isEqualTo("");
  }

  @Test
  void convertFlatPropsToNested_deeplyNestedProperties() {
    Map<String, Object> flatProps = new HashMap<>();
    flatProps.put("a.b.c.d.e", "deep-value");

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);
    assertThat(yaml).isEqualTo("");
  }

  @Test
  void convertFlatPropsToNested_nestedArrays() {
    Map<String, Object> flatProps = new HashMap<>();
    flatProps.put("outer[0].inner[0]", "value1");
    flatProps.put("outer[0].inner[1]", "value2");
    flatProps.put("outer[1].inner[0]", "value3");

    String yaml = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);
    assertThat(yaml).isEqualTo("");
  }
}
