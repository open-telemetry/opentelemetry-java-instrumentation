/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.status.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LogbackAppenderTest {

  private static final Class<?> openTelemetryAppenderClass =
      io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.class;
  private static final Class<?> openTelemetryMdcAppenderClass =
      io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender.class;

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
    OpenTelemetry openTelemetry() {
      return testing.getOpenTelemetry();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "logback-test.xml, false",
    "logback-test-no-mdc.xml, false",
    "logback-test.xml, true",
    "logback-test-no-mdc.xml, true"
  })
  void shouldInitializeAppender(String configurationFile, boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:" + configurationFile);
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.mdc_attributes/development.included",
          "key*");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.mdc_attributes/development.excluded",
          "key2");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_mdc_attributes/development",
          "key2");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_code_attributes/development",
          false);
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_template/development",
          true);
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.mdc-attributes.included", "key*");
      properties.put(
          "otel.instrumentation.logback-appender.experimental.mdc-attributes.excluded", "key2");
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes", "key2");
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-code-attributes", false);
      properties.put("otel.instrumentation.logback-appender.experimental.capture-template", true);
    }

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    assertThat(countAppenders(openTelemetryAppenderClass)).isEqualTo(1);
    assertThat(countAppenders(openTelemetryMdcAppenderClass)).isEqualTo(1);

    ListAppender<ILoggingEvent> listAppender = getListAppender();
    listAppender.list.clear();

    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      LoggerFactory.getLogger("test").info("test log message: {}", "arg");
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
              assertThat(logRecord.getBodyValue().asString()).contains("test log message: arg");

              Attributes attributes = logRecord.getAttributes();
              // key1, the code attributes should not be present because they are enabled
              // in the logback.xml file but are disabled with a property
              assertThat(attributes.asMap())
                  .hasSize(2)
                  .containsEntry(stringKey("key1"), "val1")
                  .containsEntry(stringKey("log.body.template"), "test log message: {}");
            });

    assertThat(listAppender.list)
        .satisfiesExactly(
            event ->
                assertThat(event)
                    .satisfies(
                        e -> assertThat(e.getMessage()).isEqualTo("test log message: {}"),
                        e -> assertThat(e.getMDCPropertyMap()).containsOnlyKeys("key1", "key2")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedMdcPropertySelectsKeysLiterally(boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test-no-mdc.xml");
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_mdc_attributes/development",
          "*,key1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_code_attributes/development",
          false);
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes", "*,key1");
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-code-attributes", false);
    }

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);
    testing.clearData();

    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      LoggerFactory.getLogger("test").info("legacy MDC property");
    } finally {
      MDC.clear();
    }

    assertThat(testing.logRecords())
        .satisfiesOnlyOnce(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsExactly(entry(stringKey("key1"), "val1")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedMdcPropertyWithSoleWildcardSelectsEveryKey(boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test-no-mdc.xml");
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_mdc_attributes/development",
          "*");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_code_attributes/development",
          false);
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes", "*");
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-code-attributes", false);
    }

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);
    testing.clearData();

    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      LoggerFactory.getLogger("test").info("legacy MDC property");
    } finally {
      MDC.clear();
    }

    assertThat(testing.logRecords())
        .satisfiesOnlyOnce(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsOnly(
                        entry(stringKey("key1"), "val1"), entry(stringKey("key2"), "val2")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void excludedOnlyMdcPropertySelectsEveryOtherKey(boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test-no-mdc.xml");
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.mdc_attributes/development.excluded",
          "key2");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_code_attributes/development",
          false);
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.mdc-attributes.excluded", "key2");
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-code-attributes", false);
    }

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);
    testing.clearData();

    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      LoggerFactory.getLogger("test").info("excluded MDC property");
    } finally {
      MDC.clear();
    }

    assertThat(testing.logRecords())
        .satisfiesOnlyOnce(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsExactly(entry(stringKey("key1"), "val1")));
  }

  @Test
  void declarativeYamlSequenceMdcSelector() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test-no-mdc.xml");
    properties.put("otel.file_format", "1.1");
    // a YAML sequence is flattened by the Spring environment into indexed properties
    properties.put(
        "otel.instrumentation/development.java.logback_appender.mdc_attributes/development.included[0]",
        "request-*");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.mdc_attributes/development.included[1]",
        "user-?");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.mdc_attributes/development.excluded[0]",
        "*-secret");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.capture_code_attributes/development",
        false);

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);
    testing.clearData();

    MDC.put("request-id", "123");
    MDC.put("user-1", "alice");
    MDC.put("user-name", "ignored");
    MDC.put("request-secret", "shh");
    try {
      LoggerFactory.getLogger("test").info("declarative MDC selector");
    } finally {
      MDC.clear();
    }

    assertThat(testing.logRecords())
        .satisfiesOnlyOnce(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsOnly(
                        entry(stringKey("request-id"), "123"),
                        entry(stringKey("user-1"), "alice")));
  }

  @Test
  void declarativeYamlSequenceDeprecatedMdcProperty() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test-no-mdc.xml");
    properties.put("otel.file_format", "1.1");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.capture_mdc_attributes/development[0]",
        "*");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.capture_mdc_attributes/development[1]",
        "key1");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.capture_code_attributes/development",
        false);

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);
    testing.clearData();

    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      LoggerFactory.getLogger("test").info("declarative deprecated MDC property");
    } finally {
      MDC.clear();
    }

    assertThat(testing.logRecords())
        .satisfiesOnlyOnce(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsExactly(entry(stringKey("key1"), "val1")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void keyValuePairSelectorFromPropertiesTakesPrecedenceOverDeprecatedProperty(
      boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_key_value_pair_attributes/development",
          true);
      properties.put(
          "otel.instrumentation/development.java.logback_appender.key_value_pair_attributes/development.included",
          "key1");
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes",
          true);
      properties.put(
          "otel.instrumentation.logback-appender.experimental.key-value-pair-attributes.included",
          "key1");
    }

    assertThat(keyValuePairDeprecationWarnings(properties)).isEmpty();
  }

  @Test
  void declarativeYamlSequenceKeyValuePairSelectorTakesPrecedenceOverDeprecatedProperty() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("otel.file_format", "1.1");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.capture_key_value_pair_attributes/development",
        true);
    // a YAML sequence is flattened by the Spring environment into indexed properties
    properties.put(
        "otel.instrumentation/development.java.logback_appender.key_value_pair_attributes/development.excluded[0]",
        "secret");

    assertThat(keyValuePairDeprecationWarnings(properties)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedKeyValuePairPropertyWarns(boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_key_value_pair_attributes/development",
          true);
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes",
          true);
    }

    assertThat(keyValuePairDeprecationWarnings(properties)).hasSize(1);
  }

  @Test
  @SuppressWarnings("deprecation") // verifies the deprecated setting keeps its meaning
  void emptyKeyValuePairSelectorPropertyDoesNotReplaceAppenderSettings() {
    Map<String, Object> properties = new HashMap<>();
    // an empty property value cannot be distinguished from an unset one, so it leaves the settings
    // declared in logback.xml alone
    properties.put(
        "otel.instrumentation.logback-appender.experimental.key-value-pair-attributes.included",
        "");

    assertThat(
            keyValuePairDeprecationWarnings(
                properties, appender -> appender.setCaptureKeyValuePairAttributes(true)))
        .hasSize(1);
  }

  @Test
  @SuppressWarnings("deprecation") // verifies the deprecated setting is replaced
  void keyValuePairSelectorPropertyTakesPrecedenceOverDeprecatedAppenderSetting() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(
        "otel.instrumentation.logback-appender.experimental.key-value-pair-attributes.included",
        "key1");

    assertThat(
            keyValuePairDeprecationWarnings(
                properties, appender -> appender.setCaptureKeyValuePairAttributes(true)))
        .isEmpty();
  }

  /**
   * Applies {@code properties} to a fresh appender and returns the deprecation warnings the
   * appender reported while resolving its key value pair selector.
   */
  private static List<Status> keyValuePairDeprecationWarnings(Map<String, Object> properties) {
    return keyValuePairDeprecationWarnings(properties, appender -> {});
  }

  /**
   * Applies {@code properties} to an appender prepared by {@code declaredInXml}, simulating the
   * settings of an appender declared in {@code logback.xml}, and returns the deprecation warnings
   * the appender reported while resolving its key value pair selector.
   */
  private static List<Status> keyValuePairDeprecationWarnings(
      Map<String, Object> properties, Consumer<OpenTelemetryAppender> declaredInXml) {
    return deprecationWarnings(
        properties,
        declaredInXml,
        LogbackAppenderInstaller::initializeKeyValuePairAttributesFromProperties,
        "otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes");
  }

  /**
   * Applies {@code properties} to an appender prepared by {@code declaredInXml}, simulating the
   * settings of an appender declared in {@code logback.xml}, and returns the deprecation warnings
   * the appender reported while resolving its MDC selector.
   */
  private static List<Status> mdcDeprecationWarnings(
      Map<String, Object> properties, Consumer<OpenTelemetryAppender> declaredInXml) {
    return deprecationWarnings(
        properties,
        declaredInXml,
        LogbackAppenderInstaller::initializeMdcAttributesFromProperties,
        "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes");
  }

  private static List<Status> deprecationWarnings(
      Map<String, Object> properties,
      Consumer<OpenTelemetryAppender> declaredInXml,
      BiConsumer<ConfigurableEnvironment, OpenTelemetryAppender> initializeFromProperties,
      String deprecatedProperty) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.setContext(new LoggerContext());
    appender.setOpenTelemetry(OpenTelemetry.noop());
    declaredInXml.accept(appender);

    initializeFromProperties.accept(environment, appender);
    appender.start();

    return appender.getContext().getStatusManager().getCopyOfStatusList().stream()
        .filter(
            status ->
                status.getMessage() != null && status.getMessage().contains(deprecatedProperty))
        .collect(toList());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void loggerContextSelectorFromPropertiesTakesPrecedenceOverDeprecatedProperty(
      boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_logger_context_attributes/development",
          true);
      properties.put(
          "otel.instrumentation/development.java.logback_appender.logger_context_attributes/development.included",
          "key1");
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes",
          true);
      properties.put(
          "otel.instrumentation.logback-appender.experimental.logger-context-attributes.included",
          "key1");
    }

    assertThat(loggerContextDeprecationWarnings(properties)).isEmpty();
  }

  @Test
  void declarativeYamlSequenceLoggerContextSelectorTakesPrecedenceOverDeprecatedProperty() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("otel.file_format", "1.1");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.capture_logger_context_attributes/development",
        true);
    // a YAML sequence is flattened by the Spring environment into indexed properties
    properties.put(
        "otel.instrumentation/development.java.logback_appender.logger_context_attributes/development.excluded[0]",
        "secret");

    assertThat(loggerContextDeprecationWarnings(properties)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedLoggerContextPropertyWarns(boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_logger_context_attributes/development",
          true);
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes",
          true);
    }

    assertThat(loggerContextDeprecationWarnings(properties)).hasSize(1);
  }

  @Test
  @SuppressWarnings("deprecation") // verifies the deprecated setting keeps its meaning
  void emptyLoggerContextSelectorPropertyDoesNotReplaceAppenderSettings() {
    Map<String, Object> properties = new HashMap<>();
    // an empty property value cannot be distinguished from an unset one, so it leaves the settings
    // declared in logback.xml alone
    properties.put(
        "otel.instrumentation.logback-appender.experimental.logger-context-attributes.included",
        "");

    assertThat(
            loggerContextDeprecationWarnings(
                properties, appender -> appender.setCaptureLoggerContext(true)))
        .hasSize(1);
  }

  @Test
  @SuppressWarnings("deprecation") // verifies the deprecated setting is replaced
  void loggerContextSelectorPropertyTakesPrecedenceOverDeprecatedAppenderSetting() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(
        "otel.instrumentation.logback-appender.experimental.logger-context-attributes.included",
        "key1");

    assertThat(
            loggerContextDeprecationWarnings(
                properties, appender -> appender.setCaptureLoggerContext(true)))
        .isEmpty();
  }

  /**
   * Applies {@code properties} to a fresh appender and returns the deprecation warnings the
   * appender reported while resolving its logger context selector.
   */
  private static List<Status> loggerContextDeprecationWarnings(Map<String, Object> properties) {
    return loggerContextDeprecationWarnings(properties, appender -> {});
  }

  /**
   * Applies {@code properties} to an appender prepared by {@code declaredInXml}, simulating the
   * settings of an appender declared in {@code logback.xml}, and returns the deprecation warnings
   * the appender reported while resolving its logger context selector.
   */
  private static List<Status> loggerContextDeprecationWarnings(
      Map<String, Object> properties, Consumer<OpenTelemetryAppender> declaredInXml) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.setContext(new LoggerContext());
    appender.setOpenTelemetry(OpenTelemetry.noop());
    declaredInXml.accept(appender);

    LogbackAppenderInstaller.initializeLoggerContextAttributesFromProperties(environment, appender);
    appender.start();

    return appender.getContext().getStatusManager().getCopyOfStatusList().stream()
        .filter(
            status ->
                status.getMessage() != null
                    && status
                        .getMessage()
                        .contains(
                            "otel.instrumentation.logback-appender.experimental"
                                + ".capture-logger-context-attributes"))
        .collect(toList());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void logstashMarkerSelectorFromPropertiesTakesPrecedenceOverDeprecatedProperty(
      boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_logstash_marker_attributes/development",
          true);
      properties.put(
          "otel.instrumentation/development.java.logback_appender.logstash_marker_attributes/development.included",
          "key1");
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-logstash-marker-attributes",
          true);
      properties.put(
          "otel.instrumentation.logback-appender.experimental.logstash-marker-attributes.included",
          "key1");
    }

    assertThat(logstashMarkerDeprecationWarnings(properties)).isEmpty();
  }

  @Test
  void declarativeYamlSequenceLogstashMarkerSelectorTakesPrecedenceOverDeprecatedProperty() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("otel.file_format", "1.1");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.capture_logstash_marker_attributes/development",
        true);
    // a YAML sequence is flattened by the Spring environment into indexed properties
    properties.put(
        "otel.instrumentation/development.java.logback_appender.logstash_marker_attributes/development.excluded[0]",
        "secret");

    assertThat(logstashMarkerDeprecationWarnings(properties)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedLogstashMarkerPropertyWarns(boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_logstash_marker_attributes/development",
          true);
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-logstash-marker-attributes",
          true);
    }

    assertThat(logstashMarkerDeprecationWarnings(properties)).hasSize(1);
  }

  @Test
  @SuppressWarnings("deprecation") // verifies the deprecated setting keeps its meaning
  void emptyLogstashMarkerSelectorPropertyDoesNotReplaceAppenderSettings() {
    Map<String, Object> properties = new HashMap<>();
    // an empty property value cannot be distinguished from an unset one, so it leaves the settings
    // declared in logback.xml alone
    properties.put(
        "otel.instrumentation.logback-appender.experimental.logstash-marker-attributes.included",
        "");

    assertThat(
            logstashMarkerDeprecationWarnings(
                properties, appender -> appender.setCaptureLogstashMarkerAttributes(true)))
        .hasSize(1);
  }

  @Test
  @SuppressWarnings("deprecation") // verifies the deprecated setting is replaced
  void logstashMarkerSelectorPropertyTakesPrecedenceOverDeprecatedAppenderSetting() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(
        "otel.instrumentation.logback-appender.experimental.logstash-marker-attributes.included",
        "key1");

    assertThat(
            logstashMarkerDeprecationWarnings(
                properties, appender -> appender.setCaptureLogstashMarkerAttributes(true)))
        .isEmpty();
  }

  /**
   * Applies {@code properties} to a fresh appender and returns the deprecation warnings the
   * appender reported while resolving its Logstash marker selector.
   */
  private static List<Status> logstashMarkerDeprecationWarnings(Map<String, Object> properties) {
    return logstashMarkerDeprecationWarnings(properties, appender -> {});
  }

  /**
   * Applies {@code properties} to an appender prepared by {@code declaredInXml}, simulating the
   * settings of an appender declared in {@code logback.xml}, and returns the deprecation warnings
   * the appender reported while resolving its Logstash marker selector.
   */
  private static List<Status> logstashMarkerDeprecationWarnings(
      Map<String, Object> properties, Consumer<OpenTelemetryAppender> declaredInXml) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.setContext(new LoggerContext());
    appender.setOpenTelemetry(OpenTelemetry.noop());
    declaredInXml.accept(appender);

    LogbackAppenderInstaller.initializeLogstashMarkerAttributesFromProperties(
        environment, appender);
    appender.start();

    return appender.getContext().getStatusManager().getCopyOfStatusList().stream()
        .filter(
            status ->
                status.getMessage() != null
                    && status
                        .getMessage()
                        .contains(
                            "otel.instrumentation.logback-appender.experimental"
                                + ".capture-logstash-marker-attributes"))
        .collect(toList());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void logstashStructuredArgumentSelectorFromPropertiesTakesPrecedenceOverDeprecatedProperty(
      boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_logstash_structured_arguments/development",
          true);
      properties.put(
          "otel.instrumentation/development.java.logback_appender.logstash_structured_argument_attributes/development.included",
          "key1");
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-logstash-structured-arguments",
          true);
      properties.put(
          "otel.instrumentation.logback-appender.experimental.logstash-structured-argument-attributes.included",
          "key1");
    }

    assertThat(logstashStructuredArgumentDeprecationWarnings(properties)).isEmpty();
  }

  @Test
  void
      declarativeYamlSequenceLogstashStructuredArgumentSelectorTakesPrecedenceOverDeprecatedProperty() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("otel.file_format", "1.1");
    properties.put(
        "otel.instrumentation/development.java.logback_appender.capture_logstash_structured_arguments/development",
        true);
    // a YAML sequence is flattened by the Spring environment into indexed properties
    properties.put(
        "otel.instrumentation/development.java.logback_appender.logstash_structured_argument_attributes/development.excluded[0]",
        "secret");

    assertThat(logstashStructuredArgumentDeprecationWarnings(properties)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedLogstashStructuredArgumentPropertyWarns(boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.instrumentation/development.java.logback_appender.capture_logstash_structured_arguments/development",
          true);
    } else {
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-logstash-structured-arguments",
          true);
    }

    assertThat(logstashStructuredArgumentDeprecationWarnings(properties)).hasSize(1);
  }

  /**
   * Applies {@code properties} to a fresh appender and returns the deprecation warnings the
   * appender reported while resolving its Logstash structured argument selector.
   */
  private static List<Status> logstashStructuredArgumentDeprecationWarnings(
      Map<String, Object> properties) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.setContext(new LoggerContext());
    appender.setOpenTelemetry(OpenTelemetry.noop());

    LogbackAppenderInstaller.initializeLogstashStructuredArgumentAttributesFromProperties(
        environment, appender);
    appender.start();

    return appender.getContext().getStatusManager().getCopyOfStatusList().stream()
        .filter(
            status ->
                status.getMessage() != null
                    && status
                        .getMessage()
                        .contains(
                            "otel.instrumentation.logback-appender.experimental"
                                + ".capture-logstash-structured-arguments"))
        .collect(toList());
  }

  @Test
  @SuppressWarnings("deprecation") // verifies the deprecated setting keeps its meaning
  void emptyMdcSelectorPropertyDoesNotReplaceAppenderSettings() {
    Map<String, Object> properties = new HashMap<>();
    // an empty property value cannot be distinguished from an unset one, so it leaves the settings
    // declared in logback.xml alone
    properties.put(
        "otel.instrumentation.logback-appender.experimental.mdc-attributes.included", "");

    assertThat(
            mdcDeprecationWarnings(
                properties, appender -> appender.setCaptureMdcAttributes("key1")))
        .hasSize(1);
  }

  @Test
  void deprecatedMdcPropertyDoesNotWarnWhenReplacementConfigured() {
    ch.qos.logback.classic.Logger installerLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LogbackAppenderInstaller.class);
    ListAppender<ILoggingEvent> warningAppender = new ListAppender<>();
    warningAppender.start();
    installerLogger.addAppender(warningAppender);
    try {
      Map<String, Object> properties = new HashMap<>();
      properties.put(
          "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes", "key1");
      properties.put(
          "otel.instrumentation.logback-appender.experimental.mdc-attributes.included", "key2");
      StandardEnvironment environment = new StandardEnvironment();
      environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

      LogbackAppenderInstaller.initializeMdcAttributesFromProperties(
          environment, new OpenTelemetryAppender());

      assertThat(warningAppender.list)
          .filteredOn(
              event ->
                  event
                      .getFormattedMessage()
                      .contains(
                          "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes"))
          .isEmpty();
    } finally {
      installerLogger.detachAppender(warningAppender);
      warningAppender.stop();
    }
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

    assertThat(countAppenders(openTelemetryAppenderClass)).isEqualTo(1);
    assertThat(countAppenders(openTelemetryMdcAppenderClass)).isEqualTo(1);

    LoggerFactory.getLogger("test").info("test log message");

    assertThat(testing.logRecords()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mdcAppender(boolean declarativeConfig) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test.xml");
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
      properties.put(
          "otel.distribution.spring_starter.instrumentation.disabled[0]", "logback_appender");
      properties.put("otel.instrumentation/development.java.logback_mdc.add_baggage", "true");
      properties.put(
          "otel.instrumentation/development.java.common.logging.trace_id_key", "traceid");
      properties.put("otel.instrumentation/development.java.common.logging.span_id_key", "spanid");
      properties.put(
          "otel.instrumentation/development.java.common.logging.trace_flags_key", "traceflags");
    } else {
      properties.put("otel.instrumentation.logback-appender.enabled", "false");
      properties.put("otel.instrumentation.logback-mdc.add-baggage", "true");
      properties.put("otel.instrumentation.common.logging.trace-id-key", "traceid");
      properties.put("otel.instrumentation.common.logging.span-id-key", "spanid");
      properties.put("otel.instrumentation.common.logging.trace-flags-key", "traceflags");
    }

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

    assertThat(countAppenders(openTelemetryAppenderClass)).isEqualTo(0);
    assertThat(countAppenders(openTelemetryMdcAppenderClass)).isEqualTo(1);

    ListAppender<ILoggingEvent> listAppender = getListAppender();
    listAppender.list.clear();

    Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
    try (Scope ignore = span.makeCurrent()) {
      LoggerFactory.getLogger("test").info("test log message");
    }

    assertThat(testing.logRecords()).isEmpty();
    assertThat(CustomListAppender.lastLogHadTraceId).isTrue();
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

    assertThat(countAppenders(openTelemetryAppenderClass)).isEqualTo(0);
    assertThat(countAppenders(openTelemetryMdcAppenderClass)).isEqualTo(0);

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
    if (mdcAppender == null) {
      for (Iterator<Appender<ILoggingEvent>> i = logbackLogger.iteratorForAppenders();
          i.hasNext(); ) {
        Appender<ILoggingEvent> appender = i.next();
        if (appender
            instanceof io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender) {
          mdcAppender = (AppenderAttachable<?>) appender;
          break;
        }
      }
    }
    return (ListAppender<ILoggingEvent>) mdcAppender.getAppender("List");
  }

  private static int countAppenders(Appender<?> appender, Class<?> appenderClass) {
    int count = 0;
    if (appenderClass.isInstance(appender)) {
      count++;
    } else if (appender instanceof AppenderAttachable) {
      for (Iterator<? extends Appender<?>> iterator =
              ((AppenderAttachable<?>) appender).iteratorForAppenders();
          iterator.hasNext(); ) {
        Appender<?> childAppender = iterator.next();
        count += countAppenders(childAppender, appenderClass);
      }
    }
    return count;
  }

  private static int countAppenders(Class<?> appenderClass) {
    ILoggerFactory loggerFactorySpi = LoggerFactory.getILoggerFactory();
    if (!(loggerFactorySpi instanceof LoggerContext)) {
      return 0;
    }
    int count = 0;
    LoggerContext loggerContext = (LoggerContext) loggerFactorySpi;
    for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
      Iterator<Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
      while (appenderIterator.hasNext()) {
        Appender<ILoggingEvent> appender = appenderIterator.next();
        count += countAppenders(appender, appenderClass);
      }
    }
    return count;
  }
}
