/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import net.logstash.logback.argument.StructuredArguments;
import net.logstash.logback.marker.Markers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LogbackStructuredAttributesTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("structured-attributes");

  @Test
  void selectsAllStructuredSourcesWithoutSelectingAmbientContext() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.putProperty("ambient-context", "not-captured");
    MDC.put("ambient-mdc", "not-captured");
    MDC.put("otel.event.name", "mdc-event");
    try {
      logger
          .atInfo()
          .addMarker(
              Markers.append("key1", 1)
                  .and(Markers.append("key2", "marker"))
                  .and(Markers.append("other", "marker"))
                  .and(Markers.append("otel.event.name", "marker-event")))
          .addKeyValue("key1", 42)
          .addKeyValue("otel.event.name", "kvp-event")
          .log(
              "structured {} {}",
              StructuredArguments.keyValue("key2", "argument"),
              StructuredArguments.keyValue("otel.event.name", "argument-event"));
    } finally {
      MDC.clear();
      loggerContext.putProperty("ambient-context", null);
    }

    testing.waitAndAssertLogRecords(
        logRecord -> {
          logRecord.hasEventName("kvp-event");
          Attributes attributes = logRecord.actual().getAttributes();
          String configuration =
              System.getProperty("testLogstashStructuredArgsConfiguration", "new");
          boolean capturesKey1 = !configuration.equals("none");
          boolean capturesKey2 = capturesKey1 && !configuration.equals("precedence");
          boolean capturesOther =
              configuration.equals("default")
                  || configuration.equals("empty")
                  || configuration.equals("legacy");
          // Only the structured-argument source is enabled by the legacy test task.
          assertThat(attributes.get(longKey("key1")))
              .isEqualTo(capturesKey1 && !configuration.equals("legacy") ? 42L : null);
          assertThat(attributes.get(stringKey("key1"))).isNull();
          assertThat(attributes.get(stringKey("key2"))).isEqualTo(capturesKey2 ? "argument" : null);
          assertThat(attributes.get(stringKey("other")))
              .isEqualTo(capturesOther && !configuration.equals("legacy") ? "marker" : null);
          assertThat(attributes.get(stringKey("otel.event.name"))).isNull();
          assertThat(attributes.get(stringKey("ambient-context"))).isNull();
          assertThat(attributes.get(stringKey("ambient-mdc"))).isNull();
        });
  }
}
