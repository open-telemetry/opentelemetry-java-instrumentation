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
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ProcessResourceTest {

  @Test
  void testCurrentPlatform() {
    String osName = System.getProperty("os.name");
    boolean windows = osName != null && osName.toLowerCase(Locale.ROOT).startsWith("windows");
    assertResource(windows);
  }

  private static void assertResource(boolean windows) {
    Resource resource = ProcessResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH))
        .matches(windows ? ".*[/\\\\]java\\.exe" : ".*[/\\\\]java");

    // With Java 9+ and a compiled jar, ResourceAttributes.PROCESS_COMMAND_ARGS
    // will be set instead of ResourceAttributes.PROCESS_COMMAND_LINE
    // However, on Java 9+ when the command line is too long, the argument array
    // may be empty and PROCESS_COMMAND_LINE will be set instead.
    if (attributes.get(ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS) != null) {
      // Java 9+ with short command line
      assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS))
          .contains(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH))
          .contains("-DtestSecret=***")
          .contains("-DtestPassword=***")
          .contains("-DtestNotRedacted=test");
    } else {
      // Java 8 or Java 9+ with long command line
      assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_COMMAND_LINE))
          .contains(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH))
          .contains("-DtestSecret=***")
          .contains("-DtestPassword=***")
          .contains("-DtestNotRedacted=test");
    }
  }
}
