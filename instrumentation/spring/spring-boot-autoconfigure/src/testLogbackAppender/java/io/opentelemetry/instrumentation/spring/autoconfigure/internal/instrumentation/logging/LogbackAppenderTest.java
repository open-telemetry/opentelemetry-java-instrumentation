/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.AppenderAttachable;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class LogbackAppenderTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeEach
  void setUp() {
    // reset the appender
    OpenTelemetryAppender.install(null);
  }

  @Configuration
  static class TestingOpenTelemetryConfiguration {

    @Bean
    public OpenTelemetry openTelemetry() {
      return testing.getOpenTelemetry();
    }
  }

  @Test
  void shouldInitializeAppender() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test.xml");
    properties.put(
        "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes", "*");
    properties.put(
        "otel.instrumentation.logback-appender.experimental.capture-code-attributes", false);

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    ListAppender<ILoggingEvent> listAppender = getListAppender();
    listAppender.list.clear();

    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      LoggerFactory.getLogger("test").info("test log message");
    } finally {
      MDC.clear();
    }

    List<LogRecordData> logRecords = testing.logRecords();
    assertThat(logRecords)
        .satisfiesOnlyOnce(
            // OTel appender automatically added or from an XML file, it should not
            // be added a second time by LogbackAppenderApplicationListener
            logRecord -> {
              assertThat(logRecord.getInstrumentationScopeInfo().getName()).isEqualTo("test");
              assertThat(logRecord.getBodyValue().asString()).contains("test log message");

              Attributes attributes = logRecord.getAttributes();
              // key1 and key2, the code attributes should not be present because they are enabled
              // in the logback.xml file but are disabled with a property
              assertThat(attributes.size()).isEqualTo(2);
              assertThat(attributes.asMap())
                  .containsEntry(AttributeKey.stringKey("key1"), "val1")
                  .containsEntry(AttributeKey.stringKey("key2"), "val2");
            });

    assertThat(listAppender.list)
        .satisfiesExactly(
            event ->
                assertThat(event)
                    .satisfies(
                        e -> assertThat(e.getMessage()).isEqualTo("test log message"),
                        e -> assertThat(e.getMDCPropertyMap()).containsOnlyKeys("key1", "key2")));
  }

  @Test
  void shouldNotInitializeAppenderWhenDisabled() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test.xml");
    properties.put("otel.instrumentation.logback-appender.enabled", "false");

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    LoggerFactory.getLogger("test").info("test log message");

    assertThat(testing.logRecords()).isEmpty();
  }

  @Test
  void mdcAppender() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test.xml");
    properties.put("otel.instrumentation.logback-appender.enabled", "false");
    properties.put("otel.instrumentation.logback-mdc.add-baggage", "true");
    properties.put("otel.instrumentation.common.logging.trace-id", "traceid");
    properties.put("otel.instrumentation.common.logging.span-id", "spanid");
    properties.put("otel.instrumentation.common.logging.trace-flags", "traceflags");

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    ListAppender<ILoggingEvent> listAppender = getListAppender();
    listAppender.list.clear();

    try (Scope ignore = Baggage.current().toBuilder().put("key", "value").build().makeCurrent()) {
      Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
      try (Scope ignore2 = span.makeCurrent()) {
        LoggerFactory.getLogger("test").info("test log message");
      }
    }

    assertThat(testing.logRecords()).isEmpty();
    assertThat(listAppender.list)
        .satisfiesExactly(
            event ->
                assertThat(event)
                    .satisfies(
                        e -> assertThat(e.getMessage()).isEqualTo("test log message"),
                        e ->
                            assertThat(e.getMDCPropertyMap())
                                .containsOnlyKeys(
                                    "traceid", "spanid", "traceflags", "baggage.key")));
  }

  @Test
  void shouldInitializeMdcAppender() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-no-otel-appenders.xml");
    properties.put("otel.instrumentation.logback-appender.enabled", "false");

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    ListAppender<ILoggingEvent> listAppender = getListAppender();
    listAppender.list.clear();

    Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
    try (Scope ignore = span.makeCurrent()) {
      LoggerFactory.getLogger("test").info("test log message");
    }

    assertThat(testing.logRecords()).isEmpty();
    assertThat(listAppender.list)
        .satisfiesExactly(
            event ->
                assertThat(event)
                    .satisfies(
                        e -> assertThat(e.getMessage()).isEqualTo("test log message"),
                        e ->
                            assertThat(e.getMDCPropertyMap())
                                .containsOnlyKeys("trace_id", "span_id", "trace_flags")));
  }

  @Test
  void shouldNotInitializeMdcAppenderWhenDisabled() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-no-otel-appenders.xml");
    properties.put("otel.instrumentation.logback-appender.enabled", "false");
    properties.put("otel.instrumentation.logback-mdc.enabled", "false");

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    ListAppender<ILoggingEvent> listAppender = getListAppender();
    listAppender.list.clear();

    Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
    try (Scope ignore = span.makeCurrent()) {
      LoggerFactory.getLogger("test").info("test log message");
    }

    assertThat(testing.logRecords()).isEmpty();
    assertThat(listAppender.list)
        .satisfiesExactly(
            event ->
                assertThat(event)
                    .satisfies(
                        e -> assertThat(e.getMessage()).isEqualTo("test log message"),
                        e -> assertThat(e.getMDCPropertyMap()).isEmpty()));
  }

  @SuppressWarnings("unchecked")
  private static ListAppender<ILoggingEvent> getListAppender() {
    Logger logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) logger;
    ListAppender<ILoggingEvent> listAppender =
        (ListAppender<ILoggingEvent>) logbackLogger.getAppender("List");
    if (listAppender != null) {
      return listAppender;
    }
    AppenderAttachable<?> mdcAppender =
        (AppenderAttachable<?>) logbackLogger.getAppender("OpenTelemetryMdc");
    return (ListAppender<ILoggingEvent>) mdcAppender.getAppender("List");
  }
}
