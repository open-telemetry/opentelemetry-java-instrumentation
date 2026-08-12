/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v1_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Log4jMdcSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = Logger.getLogger("selector");

  @Test
  void capturesConfiguredMdcAttributes() {
    MDC.put("exact", "exact-value");
    MDC.put("prefix.value", "prefix-value");
    MDC.put("prefix.secret", "excluded-value");
    MDC.put("single1", "single-value");
    MDC.put("single22", "ignored-value");
    MDC.put("legacy", "legacy-value");
    MDC.put("new", "new-value");
    MDC.put("otel.event.name", "MyEventName");
    try {
      logger.info("selector test");
    } finally {
      MDC.remove("exact");
      MDC.remove("prefix.value");
      MDC.remove("prefix.secret");
      MDC.remove("single1");
      MDC.remove("single22");
      MDC.remove("legacy");
      MDC.remove("new");
      MDC.remove("otel.event.name");
    }

    testing.waitAndAssertLogRecords(
        logRecord -> {
          logRecord.hasEventName("MyEventName");
          Attributes attributes = logRecord.actual().getAttributes();
          String configuration = System.getProperty("testMdcConfiguration", "new");
          if (configuration.equals("new")) {
            assertThat(attributes.get(stringKey("exact"))).isEqualTo("exact-value");
            assertThat(attributes.get(stringKey("prefix.value"))).isEqualTo("prefix-value");
            assertThat(attributes.get(stringKey("single1"))).isEqualTo("single-value");
          } else if (configuration.equals("legacy")) {
            assertThat(attributes.get(stringKey("legacy"))).isEqualTo("legacy-value");
          } else if (configuration.equals("precedence")) {
            assertThat(attributes.get(stringKey("new"))).isEqualTo("new-value");
          }
          assertThat(attributes.get(stringKey("prefix.secret"))).isNull();
          assertThat(attributes.get(stringKey("single22"))).isNull();
          assertThat(attributes.get(stringKey("otel.event.name"))).isNull();
          if (!configuration.equals("new")) {
            assertThat(attributes.get(stringKey("exact"))).isNull();
            assertThat(attributes.get(stringKey("prefix.value"))).isNull();
            assertThat(attributes.get(stringKey("single1"))).isNull();
          }
          if (!configuration.equals("legacy")) {
            assertThat(attributes.get(stringKey("legacy"))).isNull();
          }
          if (!configuration.equals("precedence")) {
            assertThat(attributes.get(stringKey("new"))).isNull();
          }
        });
  }
}
