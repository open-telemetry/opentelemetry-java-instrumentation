/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class AsyncOpenTelemetryAppenderTest {
  private static final Logger logger = LoggerFactory.getLogger("TestLogger");

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeEach
  void setup() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @Test
  void captureLogMessage() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    assertThat(loggerContext.getStatusManager().getCopyOfStatusList())
        .filteredOn(status -> status.getLevel() == Status.WARN)
        .filteredOn(status -> status.getOrigin() instanceof OpenTelemetryAppender)
        .filteredOn(status -> status.getMessage().contains("is downstream of AsyncAppender"))
        .singleElement()
        .satisfies(
            status ->
                assertThat(status.getMessage())
                    .contains(
                        "OpenTelemetryAppender 'OpenTelemetry' is downstream of AsyncAppender"
                            + " 'AsyncAppender'"));
    logger.info("log message 1");

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasBody("log message 1"));
  }

  @Test
  void exportsOriginalMdcAndDeliversEnrichedMdcAsynchronously() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger testLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("TestLogger");
    ch.qos.logback.classic.Logger rootLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    AsyncAppender configuredAsyncAppender = (AsyncAppender) rootLogger.getAppender("AsyncAppender");
    rootLogger.detachAppender(configuredAsyncAppender);

    CountDownLatch producerFinished = new CountDownLatch(1);
    ListAppender<ILoggingEvent> applicationAppender = new ListAppender<>();
    applicationAppender.setContext(loggerContext);
    applicationAppender.start();

    OpenTelemetryAppender exporter = new OpenTelemetryAppender();
    exporter.setContext(loggerContext);
    exporter.setMdcAttributesIncluded("request-id,custom.trace,custom.span,custom.flags,baggage.*");
    exporter.start();

    AppenderBase<ILoggingEvent> asyncOutputAppender =
        new AppenderBase<ILoggingEvent>() {
          @Override
          protected void append(ILoggingEvent event) {
            try {
              producerFinished.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            applicationAppender.doAppend(event);
            exporter.doAppend(event);
          }
        };
    asyncOutputAppender.setContext(loggerContext);
    asyncOutputAppender.start();

    AsyncAppender asyncAppender = new AsyncAppender();
    asyncAppender.setContext(loggerContext);
    asyncAppender.setName("AsyncMdcAppender");
    asyncAppender.addAppender(asyncOutputAppender);
    asyncAppender.start();
    exporter.setOpenTelemetry(testing.getOpenTelemetry());
    rootLogger.addAppender(asyncAppender);

    io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender mdcAppender =
        new io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender();
    mdcAppender.setContext(loggerContext);
    mdcAppender.setTraceIdKey("custom.trace");
    mdcAppender.setSpanIdKey("custom.span");
    mdcAppender.setTraceFlagsKey("custom.flags");
    mdcAppender.setAddBaggage(true);
    mdcAppender.start();
    testLogger.addAppender(mdcAppender);

    Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("async-test").startSpan();
    try (Scope ignored = span.makeCurrent();
        Scope ignoredBaggage =
            Baggage.builder().put("key", "enriched-baggage").build().makeCurrent()) {
      MDC.put("request-id", "123");
      MDC.put("custom.span", "application-span");
      MDC.put("custom.flags", "application-flags");
      MDC.put("baggage.key", "application-baggage");
      logger.info("async mdc log");
    } finally {
      span.end();
      MDC.clear();
      MDC.put("request-id", "producer-after-log");
      producerFinished.countDown();
    }

    try {
      testing.waitAndAssertLogRecords(
          log ->
              log.hasBody("async mdc log")
                  .hasAttributesSatisfyingExactly(
                      equalTo(stringKey("request-id"), "123"),
                      equalTo(stringKey("custom.span"), "application-span"),
                      equalTo(stringKey("custom.flags"), "application-flags"),
                      equalTo(stringKey("baggage.key"), "application-baggage")));
      assertThat(applicationAppender.list)
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getMDCPropertyMap())
                      .containsEntry("request-id", "123")
                      .containsEntry("custom.trace", span.getSpanContext().getTraceId())
                      .containsEntry("custom.span", span.getSpanContext().getSpanId())
                      .containsEntry("custom.flags", span.getSpanContext().getTraceFlags().asHex())
                      .containsEntry("baggage.key", "enriched-baggage"));
    } finally {
      MDC.clear();
      testLogger.detachAppender(mdcAppender);
      rootLogger.detachAppender(asyncAppender);
      mdcAppender.stop();
      asyncAppender.stop();
      asyncOutputAppender.stop();
      rootLogger.addAppender(configuredAsyncAppender);
      applicationAppender.stop();
      exporter.stop();
    }
  }
}
