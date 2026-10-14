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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;

class ProcessResourceProviderTest {

  private static final boolean IS_WINDOWS = OS.WINDOWS.isCurrentOs();

  @Test
  void suppressesCommandAttributesByDefault() {
    Resource resource = createProcessResource(new HashMap<>());

    assertNoCommandAttributes(resource.getAttributes());
  }

  @Test
  void emitsCommandAttributesWithOptInOnly() {
    Map<String, String> config = new HashMap<>();
    config.put(
        "otel.instrumentation.resources.experimental.process-command-attributes.enabled", "true");

    Resource resource = createProcessResource(config);

    assertCommandAttributes(resource.getAttributes());
  }

  @Test
  void suppressesCommandAttributesWithOptOut() {
    Map<String, String> config = new HashMap<>();
    config.put(
        "otel.instrumentation.resources.experimental.process-command-attributes.enabled", "false");

    Resource resource = createProcessResource(config);

    assertNoCommandAttributes(resource.getAttributes());
  }

  private static void assertNoCommandAttributes(Attributes attributes) {
    assertThat(attributes.get(PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(PROCESS_EXECUTABLE_PATH)).isNotNull();
    assertThat(attributes.get(PROCESS_COMMAND_LINE)).isNull();
    assertThat(attributes.get(PROCESS_COMMAND_ARGS)).isNull();
  }

  private static void assertCommandAttributes(Attributes attributes) {
    assertThat(attributes.get(PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(PROCESS_EXECUTABLE_PATH)).isNotNull();
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

  private static Resource createProcessResource(Map<String, String> config) {
    return new ProcessResourceProvider()
        .createResource(DefaultConfigProperties.createFromMap(config));
  }
}
