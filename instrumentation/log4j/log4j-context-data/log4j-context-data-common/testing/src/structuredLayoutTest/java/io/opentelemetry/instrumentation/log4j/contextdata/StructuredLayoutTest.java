/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.GelfLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.layout.template.json.JsonTemplateLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StructuredLayoutTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void verifiesDefaultIgnoresAreEnabled() {
    assertThat(System.getProperty("otel.javaagent.testing.additional-library-ignores.enabled"))
        .isEqualTo("true");
  }

  @Test
  void restoresTextInjectionAfterInvalidTemplate() {
    DefaultConfiguration configuration = new DefaultConfiguration();
    try {
      JsonTemplateLayout.Builder builder =
          JsonTemplateLayout.newBuilder().setConfiguration(configuration).setEventTemplate("{");
      assertThatThrownBy(builder::build).isInstanceOf(RuntimeException.class);
      PatternLayout text =
          PatternLayout.newBuilder().withConfiguration(configuration).withPattern("%m").build();
      testing.runWithSpan(
          "after-failure",
          () -> {
            Log4jLogEvent event =
                Log4jLogEvent.newBuilder()
                    .setLevel(Level.INFO)
                    .setMessage(new SimpleMessage("hello"))
                    .build();
            assertThat(text.toSerializable(event))
                .contains(Span.current().getSpanContext().getTraceId(), "hello");
          });
    } finally {
      configuration.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"json", "gelf"})
  void preservesStructuredMessagesAndRestoresTextInjection(String format) {
    DefaultConfiguration configuration = new DefaultConfiguration();
    try {
      JsonTemplateLayout json =
          JsonTemplateLayout.newBuilder()
              .setConfiguration(configuration)
              .setEventTemplate("{\"message\":{\"$resolver\":\"pattern\",\"pattern\":\"%m\"}}")
              .build();
      GelfLayout gelf =
          GelfLayout.newBuilder()
              .setConfiguration(configuration)
              .setMessagePattern("%m")
              .setCompressionType(GelfLayout.CompressionType.OFF)
              .build();
      PatternLayout text =
          PatternLayout.newBuilder().withConfiguration(configuration).withPattern("%m").build();
      testing.runWithSpan(
          "layout",
          () -> {
            Log4jLogEvent event =
                Log4jLogEvent.newBuilder()
                    .setLevel(Level.INFO)
                    .setMessage(new SimpleMessage("hello"))
                    .build();
            if (format.equals("json")) {
              assertThat(json.toSerializable(event)).contains("\"message\":\"hello\"");
            } else {
              assertThat(gelf.toSerializable(event)).contains("\"full_message\":\"hello\"");
            }
            assertThat(text.toSerializable(event))
                .contains(Span.current().getSpanContext().getTraceId(), "hello");
          });
    } finally {
      configuration.stop();
    }
  }
}
