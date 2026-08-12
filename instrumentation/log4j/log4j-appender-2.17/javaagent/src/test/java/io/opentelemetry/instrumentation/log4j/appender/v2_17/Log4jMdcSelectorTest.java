/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Log4jMdcSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LogManager.getLogger("selector");

  @Test
  void capturesConfiguredContextDataAttributes() {
    ThreadContext.put("exact", "exact-value");
    ThreadContext.put("prefix.value", "prefix-value");
    ThreadContext.put("prefix.secret", "excluded-value");
    ThreadContext.put("single1", "single-value");
    ThreadContext.put("single22", "ignored-value");
    ThreadContext.put("legacy", "legacy-value");
    ThreadContext.put("new", "new-value");
    ThreadContext.put("otel.event.name", "MyEventName");
    try {
      logger.info("selector test");
    } finally {
      ThreadContext.clearMap();
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
