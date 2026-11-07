/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.common.AttributeKey.valueKey;
import static io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.ContextDataKeys.OTEL_CONTEXT_DATA_KEY;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.status.StatusData;
import org.apache.logging.log4j.status.StatusListener;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void logWithCarriedContextFromContextData(boolean rootContext) {
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
      Context carriedContext =
          rootContext ? Context.root() : Context.root().with(TEST_CONTEXT_KEY, "context-value");
      contextData.putValue(OTEL_CONTEXT_DATA_KEY, carriedContext);

      try (Scope ignored =
          Context.current().with(TEST_CONTEXT_KEY, "appender-thread-context").makeCurrent()) {
        appender.append(
            Log4jLogEvent.newBuilder()
                .setLoggerName("TestLogger")
                .setLevel(Level.INFO)
                .setMessage(new FormattedMessage("log message 1", (Object) null))
                .setContextData(contextData)
                .build());
      }

      await()
          .untilAsserted(
              () -> assertThat(logRecordExporter.getFinishedLogRecordItems()).hasSize(1));
      assertThat(logRecordProcessor.getContext()).isSameAs(carriedContext);
    } finally {
      openTelemetry.close();
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  void contextDataSelectorTakesPrecedenceOverDeprecatedAlias() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(
                IncludeExclude.builder().setIncluded(singletonList("new-?")).build())
            .setCaptureContextDataAttributes("legacy")
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("new-1", "captured");
    contextData.putValue("legacy", "ignored");
    contextData.putValue("otel.event.name", "MyEventName");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName("MyEventName")
                .hasAttributesSatisfyingExactly(equalTo(stringKey("new-1"), "captured")));
  }

  @Test
  @SuppressWarnings("deprecation")
  void configurationFileContextDataSelectorTakesPrecedenceOverDeprecatedAlias() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributesIncluded("request-*")
            .setContextDataAttributesExcluded("*-secret")
            .setCaptureContextDataAttributes("legacy")
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("request-id", "captured");
    contextData.putValue("request-secret", "ignored");
    contextData.putValue("legacy", "ignored");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("request-id"), "captured")));
  }

  @Test
  void programmaticContextDataSelectorTakesPrecedenceOverConfigurationFileSelector() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(
                IncludeExclude.builder().setIncluded(singletonList("request-*")).build())
            .setContextDataAttributesIncluded("other-*")
            .setContextDataAttributesExcluded("*-secret")
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("request-id", "captured");
    // the configuration file exclusion is ignored, so this is captured
    contextData.putValue("request-secret", "captured");
    // the configuration file inclusion is ignored, so this is not captured
    contextData.putValue("other-1", "ignored");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("request-id"), "captured"),
                equalTo(stringKey("request-secret"), "captured")));
  }

  @Test
  @SuppressWarnings("deprecation")
  void deprecatedAliasMatchesKeysLiterally() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setCaptureContextDataAttributes("request.*,user?,*,userId")
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("request.*", "captured");
    contextData.putValue("request.secret", "ignored");
    contextData.putValue("user?", "captured");
    contextData.putValue("user1", "ignored");
    contextData.putValue("*", "captured");
    contextData.putValue("userId", "captured");
    contextData.putValue("other", "ignored");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("request.*"), "captured"),
                equalTo(stringKey("user?"), "captured"),
                equalTo(stringKey("*"), "captured"),
                equalTo(stringKey("userId"), "captured")));
  }

  @Test
  @SuppressWarnings("deprecation")
  void deprecatedAliasWithSoleWildcardCapturesEverything() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setCaptureContextDataAttributes("*")
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("request.secret", "captured");
    contextData.putValue("userId", "captured");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("request.secret"), "captured"),
                equalTo(stringKey("userId"), "captured")));
  }

  @Test
  void emptySelectorCapturesNothing() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(IncludeExclude.builder().build())
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("userId", "ignored");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasTotalAttributeCount(0));
  }

  @Test
  void carriedContextIsNotCapturedAsContextDataAttribute() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(
                IncludeExclude.builder().setIncluded(singletonList("*")).build())
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("key", "value");
    contextData.putValue(
        OTEL_CONTEXT_DATA_KEY, Context.root().with(TEST_CONTEXT_KEY, "context-value"));
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key"), "value")));
  }

  @Test
  void stringUnderCarriedContextKeyIsCapturedAsContextDataAttribute() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(
                IncludeExclude.builder().setIncluded(singletonList("*")).build())
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue(OTEL_CONTEXT_DATA_KEY, "value");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey(OTEL_CONTEXT_DATA_KEY), "value")));
  }

  @Test
  void contextDataWithNullKeyIsCaptured() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(
                IncludeExclude.builder().setIncluded(singletonList("*")).build())
            .build();
    appender.start();

    for (Object nullKeyValue : asList(42, "value")) {
      StringMap contextData = ContextDataFactory.createContextData();
      contextData.putValue(null, nullKeyValue);
      contextData.putValue("key", "value");
      appender.append(
          Log4jLogEvent.newBuilder()
              .setLoggerName("TestLogger")
              .setLevel(Level.INFO)
              .setMessage(new FormattedMessage("log message", (Object) null))
              .setContextData(contextData)
              .build());
    }

    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key"), "value")),
        logRecord -> logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key"), "value")));
  }

  @Test
  void nonStringContextDataValuesKeepTheirType() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(
                IncludeExclude.builder().setIncluded(singletonList("*")).build())
            .build();
    appender.start();

    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("string", "value");
    contextData.putValue("boolean", true);
    contextData.putValue("short", (short) 7);
    contextData.putValue("int", 25);
    contextData.putValue("float", 0.5f);
    contextData.putValue("double", 1.5);
    contextData.putValue("string-array", new String[] {"one", "two"});
    contextData.putValue("int-array", new int[] {1, 2});
    contextData.putValue("long-list", asList(1L, 2L));
    contextData.putValue("boolean-list", asList(true, false));
    contextData.putValue("uuid", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    contextData.putValue(
        "uuid-list", singletonList(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));
    contextData.putValue("big-decimal", new BigDecimal("1.10"));
    contextData.putValue("null", null);
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("string"), "value"),
                equalTo(booleanKey("boolean"), true),
                equalTo(longKey("short"), 7),
                equalTo(longKey("int"), 25),
                equalTo(doubleKey("float"), 0.5),
                equalTo(doubleKey("double"), 1.5),
                equalTo(stringArrayKey("string-array"), asList("one", "two")),
                equalTo(longArrayKey("int-array"), asList(1L, 2L)),
                equalTo(longArrayKey("long-list"), asList(1L, 2L)),
                equalTo(booleanArrayKey("boolean-list"), asList(true, false)),
                equalTo(stringKey("uuid"), "123e4567-e89b-12d3-a456-426614174000"),
                equalTo(
                    stringArrayKey("uuid-list"),
                    singletonList("123e4567-e89b-12d3-a456-426614174000")),
                equalTo(stringKey("big-decimal"), "1.10")));
  }

  @Test
  void structuredContextDataValuesAreCapturedAsComplexAttributes() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(
                IncludeExclude.builder().setIncluded(singletonList("*")).build())
            .build();
    appender.start();

    Map<Object, Object> map = new LinkedHashMap<>();
    map.put("fn", "firstname");
    map.put("age", 25);
    map.put("numbers", asList(1, 2));
    map.put(null, "ignored");
    map.put(1, "first");
    map.put("1", "last");
    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("map", map);
    contextData.putValue("mixed-list", asList("one", 2));
    contextData.putValue("null-element", asList("one", null));
    contextData.putValue("empty-array", new String[0]);
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(
                    valueKey("map"),
                    Value.of(
                        KeyValue.of("fn", Value.of("firstname")),
                        KeyValue.of("age", Value.of(25L)),
                        KeyValue.of("numbers", Value.of(Value.of(1L), Value.of(2L))),
                        KeyValue.of("1", Value.of("last")))),
                equalTo(valueKey("mixed-list"), Value.of(Value.of("one"), Value.of(2L))),
                equalTo(valueKey("null-element"), Value.of(Value.of("one"), Value.empty())),
                equalTo(valueKey("empty-array"), Value.of(emptyList()))));
  }

  @Test
  void selfReferencingContextDataValuesAreCaptured() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setContextDataAttributes(
                IncludeExclude.builder().setIncluded(singletonList("*")).build())
            .build();
    appender.start();

    List<Object> list = new ArrayList<>();
    list.add("one");
    list.add(list);
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("self", map);
    List<Object> outer = new ArrayList<>();
    outer.add(singletonMap("outer", outer));
    List<String> shared = singletonList("value");
    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("list", list);
    contextData.putValue("map", map);
    contextData.putValue("indirect", outer);
    contextData.putValue("shared", asList(shared, shared));
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(new FormattedMessage("log message", (Object) null))
            .setContextData(contextData)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(valueKey("list"), Value.of(Value.of("one"), Value.empty())),
                equalTo(valueKey("map"), Value.of(KeyValue.of("self", Value.empty()))),
                equalTo(
                    valueKey("indirect"), Value.of(Value.of(KeyValue.of("outer", Value.empty())))),
                equalTo(
                    valueKey("shared"),
                    Value.of(Value.of(Value.of("value")), Value.of(Value.of("value"))))));
  }

  @Test
  void configurationFileContextDataSelector() {
    Logger selectorLogger = LogManager.getLogger("ContextDataSelectorTestLogger");
    ThreadContext.put("selector-included", "captured");
    ThreadContext.put("selector-secret", "ignored");
    ThreadContext.put("other", "ignored");
    try {
      selectorLogger.info("log message");
    } finally {
      ThreadContext.clearMap();
    }

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("log message")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("selector-included"), "captured")));
  }

  @Test
  void mapMessageSelector() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setStructuredAttributes(
                IncludeExclude.builder()
                    .setIncluded(singletonList("order-*"))
                    .setExcluded(singletonList("*-secret"))
                    .build())
            .build();
    appender.start();

    StringMapMessage message = new StringMapMessage();
    message.put("order-id", "captured");
    message.put("order-secret", "ignored");
    message.put("other", "ignored");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(message)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(AbstractLog4j2Test.mapMessageKey("order-id"), "captured")));
  }

  @Test
  void emptyMapMessageSelectorFallsBackToConfigurationFileSelector() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setStructuredAttributes(IncludeExclude.builder().build())
            .setStructuredAttributesIncluded("order-*")
            .build();
    appender.start();

    StringMapMessage message = new StringMapMessage();
    message.put("order-id", "captured");
    message.put("other", "ignored");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(message)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(AbstractLog4j2Test.mapMessageKey("order-id"), "captured")));
  }

  @Test
  void programmaticMapMessageSelectorTakesPrecedenceOverConfigurationFileSelector() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setStructuredAttributes(
                IncludeExclude.builder().setIncluded(singletonList("order-*")).build())
            .setStructuredAttributesIncluded("other-*")
            .setStructuredAttributesExcluded("*-secret")
            .build();
    appender.start();

    StringMapMessage message = new StringMapMessage();
    message.put("order-id", "captured");
    // the configuration file exclusion is ignored, so this is captured
    message.put("order-secret", "captured");
    // the configuration file inclusion is ignored, so this is not captured
    message.put("other-1", "ignored");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(message)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(AbstractLog4j2Test.mapMessageKey("order-id"), "captured"),
                equalTo(AbstractLog4j2Test.mapMessageKey("order-secret"), "captured")));
  }

  @Test
  void mapMessageSelectorCapturesEverythingWhenIncluded() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setStructuredAttributes(IncludeExclude.builder().setIncluded("*").build())
            .build();
    appender.start();

    StringMapMessage message = new StringMapMessage();
    message.put("order-id", "captured");
    message.put("other", "captured");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(message)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(AbstractLog4j2Test.mapMessageKey("order-id"), "captured"),
                equalTo(AbstractLog4j2Test.mapMessageKey("other"), "captured")));
  }

  @Test
  void emptyStructuredSelectorCapturesEverything() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .setStructuredAttributes(IncludeExclude.builder().build())
            .build();
    appender.start();

    StringMapMessage message = new StringMapMessage();
    message.put("order-id", "captured");
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(message)
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("order-id"), "captured")));
  }

  @Test
  void noStructuredSelectorCapturesEverything() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(testing.getOpenTelemetry())
            .build();
    appender.start();

    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("TestLogger")
            .setLevel(Level.INFO)
            .setMessage(
                new StringMapMessage().with("order-id", "captured").with("other", "captured"))
            .build());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("order-id"), "captured"),
                equalTo(stringKey("other"), "captured")));
  }

  @Test
  void configurationFileMapMessageSelector() {
    Logger selectorLogger = LogManager.getLogger("MapMessageSelectorTestLogger");
    StringMapMessage message = new StringMapMessage();
    message.put("selector-included", "captured");
    message.put("selector-secret", "ignored");
    message.put("other", "ignored");
    selectorLogger.info(message);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(AbstractLog4j2Test.mapMessageKey("selector-included"), "captured")));
  }

  @Test
  void configurationFileMapMessageSelectorCanDisableCapture() {
    StringMapMessage message = new StringMapMessage();
    message.put("value", "ignored");
    LogManager.getLogger("MapMessageSelectorDisabledTestLogger").info(message);

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
  }

  @Test
  void configurationFileWithoutStructuredSelectorCapturesEverything() {
    LogManager.getLogger("DefaultMapMessageSelectorTestLogger")
        .info(new StringMapMessage().with("key1", "value1").with("key2", "value2"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"), equalTo(stringKey("key2"), "value2")));
  }

  @Test
  void logWithOnlySpanContextStringsUsesRootContext() {
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
    StringMap contextData = ContextDataFactory.createContextData();
    contextData.putValue("trace_id", traceId);
    contextData.putValue("span_id", spanId);
    contextData.putValue("trace_flags", traceFlags);

    StatusMessageCollector statusMessages = new StatusMessageCollector();
    StatusLogger.getLogger().registerListener(statusMessages);
    try {
      appender.append(
          Log4jLogEvent.newBuilder()
              .setLoggerName("TestLogger")
              .setLevel(Level.INFO)
              .setMessage(new FormattedMessage("log message 1", (Object) null))
              .setContextData(contextData)
              .setThreadId(Thread.currentThread().getId() + 1)
              .setThreadName("application-thread")
              .build());

      testing.waitAndAssertLogRecords(
          logRecord -> logRecord.hasBody("log message 1").hasSpanContext(SpanContext.getInvalid()));
      assertThat(statusMessages.getMessages())
          .noneMatch(
              message -> message.contains("recovering span context from Log4j context data"));
    } finally {
      StatusLogger.getLogger().removeListener(statusMessages);
    }
  }

  @Test
  @SetSystemProperty(
      key = OpenTelemetryAppenderContextDataInjector.DELEGATE_CONTEXT_DATA_INJECTOR_PROPERTY,
      value =
          "io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppenderTest$TestContextDataInjector")
  void contextDataInjectorDelegatesToConfiguredInjector() {
    OpenTelemetryAppenderContextDataInjector injector =
        new OpenTelemetryAppenderContextDataInjector();

    StringMap rootContextData =
        injector.injectContextData(emptyList(), ContextDataFactory.createContextData());
    Object rootOtelContext = rootContextData.getValue(OTEL_CONTEXT_DATA_KEY);
    assertThat(rootContextData).isSameAs(TestContextDataInjector.CONTEXT_DATA);
    assertThat(rootOtelContext).isNull();

    ReadOnlyStringMap rootRawContextData = injector.rawContextData();
    Object rootRawOtelContext = rootRawContextData.getValue(OTEL_CONTEXT_DATA_KEY);
    assertThat(rootRawContextData).isSameAs(TestContextDataInjector.RAW_CONTEXT_DATA);
    assertThat(rootRawOtelContext).isNull();

    Context context = Context.current().with(TEST_CONTEXT_KEY, "context-value");
    try (Scope ignored = context.makeCurrent()) {
      StringMap contextData =
          injector.injectContextData(emptyList(), ContextDataFactory.createContextData());
      Context otelContext = contextData.getValue(OTEL_CONTEXT_DATA_KEY);
      ReadOnlyStringMap rawContextData = injector.rawContextData();
      Context rawOtelContext = rawContextData.getValue(OTEL_CONTEXT_DATA_KEY);

      assertThat((String) contextData.getValue("delegate-key")).isEqualTo("delegate-value");
      assertThat(otelContext.get(TEST_CONTEXT_KEY)).isEqualTo("context-value");
      assertThat((String) rawContextData.getValue("raw-delegate-key"))
          .isEqualTo("raw-delegate-value");
      assertThat(rawOtelContext.get(TEST_CONTEXT_KEY)).isEqualTo("context-value");
    }
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

  private static class StatusMessageCollector implements StatusListener {

    private final List<String> messages = new CopyOnWriteArrayList<>();

    @Override
    public void log(StatusData data) {
      messages.add(data.getMessage().getFormattedMessage());
    }

    @Override
    public Level getStatusLevel() {
      return Level.WARN;
    }

    @Override
    public void close() {}

    private List<String> getMessages() {
      return messages;
    }
  }

  public static class TestContextDataInjector implements ContextDataInjector {

    private static final StringMap CONTEXT_DATA = ContextDataFactory.createContextData();
    private static final StringMap RAW_CONTEXT_DATA = ContextDataFactory.createContextData();

    static {
      CONTEXT_DATA.putValue("delegate-key", "delegate-value");
      CONTEXT_DATA.freeze();
      RAW_CONTEXT_DATA.putValue("raw-delegate-key", "raw-delegate-value");
      RAW_CONTEXT_DATA.freeze();
    }

    @Override
    public StringMap injectContextData(List<Property> properties, StringMap reusable) {
      return CONTEXT_DATA;
    }

    @Override
    public ReadOnlyStringMap rawContextData() {
      return RAW_CONTEXT_DATA;
    }
  }
}
