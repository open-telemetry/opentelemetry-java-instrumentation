/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_COMMAND_LINE;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_PID;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junitpioneer.jupiter.SetSystemProperty;

class ProcessResourceTest {

  private static final boolean IS_WINDOWS = OS.WINDOWS.isCurrentOs();

  @Test
  @SetSystemProperty(key = "os.name", value = "Linux 4.12")
  void notWindows() {
    assertResource(false);
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Windows 10")
  void windows() {
    assertResource(true);
  }

  @Test
  void commandAttributesDisabled() {
    Resource resource = ProcessResource.buildResource(false);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(PROCESS_EXECUTABLE_PATH)).isNotNull();
    assertThat(attributes.get(PROCESS_COMMAND_LINE)).isNull();
    assertThat(attributes.get(PROCESS_COMMAND_ARGS)).isNull();
  }

  @Test
  void providerEmitsCommandAttributesByDefault() {
    Resource resource = createResource(new HashMap<>());

    assertCommandAttributes(resource.getAttributes());
  }

  @Test
  void providerEmitsCommandAttributesWithOptInOnly() {
    Map<String, String> config = new HashMap<>();
    config.put(
        "otel.instrumentation.resources.experimental.process-command-attributes.enabled", "true");

    Resource resource = createResource(config);

    assertCommandAttributes(resource.getAttributes());
  }

  @Test
  void providerSuppressesCommandAttributesWithV3Preview() {
    Map<String, String> config = new HashMap<>();
    config.put("otel.instrumentation.common.v3-preview", "true");

    Resource resource = createResource(config);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(PROCESS_EXECUTABLE_PATH)).isNotNull();
    assertThat(attributes.get(PROCESS_COMMAND_LINE)).isNull();
    assertThat(attributes.get(PROCESS_COMMAND_ARGS)).isNull();
  }

  @Test
  void providerEmitsCommandAttributesWithV3PreviewAndOptIn() {
    Map<String, String> config = new HashMap<>();
    config.put("otel.instrumentation.common.v3-preview", "true");
    config.put(
        "otel.instrumentation.resources.experimental.process-command-attributes.enabled", "true");

    Resource resource = createResource(config);

    assertCommandAttributes(resource.getAttributes());
  }

  private static void assertResource(boolean windows) {
    Resource resource = ProcessResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(PROCESS_EXECUTABLE_PATH))
        .matches(windows ? ".*[/\\\\]java\\.exe" : ".*[/\\\\]java");

    assertCommandAttributes(attributes);
  }

  private static void assertCommandAttributes(Attributes attributes) {
    if (isJava8() || IS_WINDOWS) {
      assertThat(attributes.get(PROCESS_COMMAND_LINE))
          .contains(attributes.get(PROCESS_EXECUTABLE_PATH))
          .contains("-DtestSecret=***")
          .contains("-DtestPassword=***")
          .contains("-DtestNotRedacted=test");
    } else {
      assertThat(attributes.get(PROCESS_COMMAND_ARGS))
          .contains(attributes.get(PROCESS_EXECUTABLE_PATH))
          .contains("-DtestSecret=***")
          .contains("-DtestPassword=***")
          .contains("-DtestNotRedacted=test");
    }
  }

  private static boolean isJava8() {
    return "1.8".equals(System.getProperty("java.specification.version"));
  }

  private static Resource createResource(Map<String, String> config) {
    return new ProcessResourceProvider().createResource(DefaultConfigProperties.createFromMap(config));
  }
}
