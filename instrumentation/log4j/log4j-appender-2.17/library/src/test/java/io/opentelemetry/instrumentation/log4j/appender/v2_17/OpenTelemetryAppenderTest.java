/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.ContextDataKeys.OTEL_CONTEXT_DATA_KEY;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.log4j.contextdata.v2_17.internal.ContextDataKeys;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetSystemProperty;

class OpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  private static final ContextKey<String> TEST_CONTEXT_KEY = ContextKey.named("test-context-key");

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeEach
  void setup() {
    generalBeforeEachSetup();
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @AfterEach
  void cleanup() {
    OpenTelemetryAppender.resetForTest();
    StatusLogger.getLogger().clear();
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Test
  void logWithFullContext() {
    ContextCapturingLogRecordProcessor logRecordProcessor =
        new ContextCapturingLogRecordProcessor();
    InMemoryLogRecordExporter logRecordExporter = InMemoryLogRecordExporter.create();
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .addLogRecordProcessor(logRecordProcessor)
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter))
                    .build())
            .build();
    OpenTelemetryAppender.resetForTest();
    OpenTelemetryAppender.install(openTelemetry);

    try {
      Context context = Context.current().with(TEST_CONTEXT_KEY, "context-value");
      try (Scope ignored = context.makeCurrent()) {
        logger.info("log message 1");
      }

      executeAfterLogsExecution();

      await()
          .untilAsserted(
              () -> assertThat(logRecordExporter.getFinishedLogRecordItems()).hasSize(1));
      assertThat(logRecordProcessor.getContext().get(TEST_CONTEXT_KEY)).isEqualTo("context-value");
    } finally {
      openTelemetry.close();
    }
  }

  @Test
  void logWithCarriedContextFromContextData() {
    ContextCapturingLogRecordProcessor logRecordProcessor =
        new ContextCapturingLogRecordProcessor();
    InMemoryLogRecordExporter logRecordExporter = InMemoryLogRecordExporter.create();
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .addLogRecordProcessor(logRecordProcessor)
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter))
                    .build())
            .build();
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(openTelemetry)
            .build();
    appender.start();

    try {
      StringMap contextData = ContextDataFactory.createContextData();
      contextData.putValue(
          OTEL_CONTEXT_DATA_KEY, Context.current().with(TEST_CONTEXT_KEY, "context-value"));

      appender.append(
          Log4jLogEvent.newBuilder()
              .setLoggerName("TestLogger")
              .setLevel(Level.INFO)
              .setMessage(new FormattedMessage("log message 1", (Object) null))
              .setContextData(contextData)
              .build());

      await()
          .untilAsserted(
              () -> assertThat(logRecordExporter.getFinishedLogRecordItems()).hasSize(1));
      assertThat(logRecordProcessor.getContext().get(TEST_CONTEXT_KEY)).isEqualTo("context-value");
    } finally {
      openTelemetry.close();
    }
  }

  @Test
  void logWithSpanContextFromContextData() {
    assumeFalse(Boolean.getBoolean("otel.instrumentation.common.v3-preview"));

    OpenTelemetryAppender.resetForTest();
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .build();
    appender.start();

    String traceId = "ff000000000000000000000000000041";
    String spanId = "ff00000000000041";
    String traceFlags = "01";
    ContextDataKeys contextDataKeys = ContextDataKeys.create(testing.getOpenTelemetry());
    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue(contextDataKeys.getTraceIdKey(), traceId);
    contextData.putValue(contextDataKeys.getSpanIdKey(), spanId);
    contextData.putValue(contextDataKeys.getTraceFlags(), traceFlags);
    StatusLogger.getLogger().clear();

    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message 1", (Object) null))
            .setContextData(contextData)
            .setThreadId(Thread.currentThread().getId() + 1)
            .setThreadName("application-thread")
            .build());

    SpanContext spanContext =
        SpanContext.create(
            traceId, spanId, TraceFlags.fromHex(traceFlags, 0), TraceState.getDefault());
    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasBody("log message 1").hasSpanContext(spanContext));
    assertThat(StatusLogger.getLogger().getStatusData())
        .extracting(statusData -> statusData.getMessage().getFormattedMessage())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(
                        "recovering span context from Log4j context data",
                        OpenTelemetryAppenderContextDataInjector.class.getName()));
  }

  @Test
  @SetSystemProperty(
      key = OpenTelemetryAppenderContextDataInjector.DELEGATE_CONTEXT_DATA_INJECTOR_PROPERTY,
      value =
          "io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppenderTest$TestContextDataInjector")
  void contextDataInjectorDelegatesToConfiguredInjector() {
    OpenTelemetryAppenderContextDataInjector injector =
        new OpenTelemetryAppenderContextDataInjector();

    StringMap contextData =
        injector.injectContextData(emptyList(), ContextDataFactory.createContextData());
    Object otelContext = contextData.getValue(OTEL_CONTEXT_DATA_KEY);

    assertThat((String) contextData.getValue("delegate-key")).isEqualTo("delegate-value");
    assertThat(otelContext).isInstanceOf(Context.class);
  }

  private static class ContextCapturingLogRecordProcessor implements LogRecordProcessor {

    private volatile Context context = Context.root();

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
      this.context = context;
    }

    @Override
    public CompletableResultCode forceFlush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }

    private Context getContext() {
      return context;
    }
  }

  public static class TestContextDataInjector implements ContextDataInjector {

    @Override
    public StringMap injectContextData(List<Property> properties, StringMap reusable) {
      reusable.putValue("delegate-key", "delegate-value");
      return reusable;
    }

    @Override
    public ReadOnlyStringMap rawContextData() {
      return ContextDataFactory.createContextData();
    }
  }
}
