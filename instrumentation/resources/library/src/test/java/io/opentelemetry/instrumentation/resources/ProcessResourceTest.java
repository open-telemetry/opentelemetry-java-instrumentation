/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
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

  private static void assertResource(boolean windows) {
    Resource resource = ProcessResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH))
        .matches(windows ? ".*[/\\\\]java\\.exe" : ".*[/\\\\]java");

    boolean java8 = "1.8".equals(System.getProperty("java.specification.version"));
    if (java8 || IS_WINDOWS) {
      assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_COMMAND_LINE))
          .contains(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH))
          .contains("-DtestSecret=***")
          .contains("-DtestPassword=***")
          .contains("-DtestNotRedacted=test");
    } else {
      assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS))
          .contains(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH))
          .contains("-DtestSecret=***")
          .contains("-DtestPassword=***")
          .contains("-DtestNotRedacted=test");
    }
  }
}
