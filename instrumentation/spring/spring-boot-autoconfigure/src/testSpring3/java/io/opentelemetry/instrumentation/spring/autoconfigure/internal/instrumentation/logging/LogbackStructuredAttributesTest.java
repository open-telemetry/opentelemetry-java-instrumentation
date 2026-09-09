/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.joran.spi.JoranException;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.logstash.logback.argument.StructuredArguments;
import net.logstash.logback.marker.Markers;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LogbackStructuredAttributesTest {

  private static final Attributes ALL_ATTRIBUTES =
      Attributes.builder()
          .put("public.value", "public")
          .put("private.value", "private")
          .put("Public.value", "case-sensitive")
          .build();

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @MethodSource("selectors")
  void commonSelectorFiltersAllStructuredSources(
      boolean declarativeConfig,
      boolean preview,
      String included,
      String excluded,
      Attributes expected) {
    Map<String, Object> properties = configuration(declarativeConfig, preview);
    putList(
        properties, declarativeConfig, "common.logging.structured-attributes.included", included);
    putList(
        properties, declarativeConfig, "common.logging.structured-attributes.excluded", excluded);

    LoggerContext context = loggerContext();
    OpenTelemetryAppender appender = appender(context);
    initialize(properties, appender);

    emitStructuredLogs(appender, context);

    assertThat(testing.logRecords())
        .extracting(LogRecordData::getAttributes)
        .containsExactly(expected, expected);
  }

  private static Stream<Arguments> selectors() {
    return Stream.of(false, true)
        .flatMap(
            declarativeConfig ->
                Stream.of(false, true)
                    .flatMap(
                        preview ->
                            Stream.of(
                                argumentSet(
                                    "absent selector, declarative="
                                        + declarativeConfig
                                        + ", preview="
                                        + preview,
                                    declarativeConfig,
                                    preview,
                                    null,
                                    null,
                                    preview ? ALL_ATTRIBUTES : Attributes.empty()),
                                argumentSet(
                                    "empty selector, declarative="
                                        + declarativeConfig
                                        + ", preview="
                                        + preview,
                                    declarativeConfig,
                                    preview,
                                    "",
                                    "",
                                    preview ? ALL_ATTRIBUTES : Attributes.empty()),
                                argumentSet(
                                    "include all, declarative="
                                        + declarativeConfig
                                        + ", preview="
                                        + preview,
                                    declarativeConfig,
                                    preview,
                                    "*",
                                    null,
                                    ALL_ATTRIBUTES),
                                argumentSet(
                                    "include globs, declarative="
                                        + declarativeConfig
                                        + ", preview="
                                        + preview,
                                    declarativeConfig,
                                    preview,
                                    "public.*,private.valu?",
                                    null,
                                    Attributes.builder()
                                        .put("public.value", "public")
                                        .put("private.value", "private")
                                        .build()),
                                argumentSet(
                                    "exclude only, declarative="
                                        + declarativeConfig
                                        + ", preview="
                                        + preview,
                                    declarativeConfig,
                                    preview,
                                    null,
                                    "private.*",
                                    Attributes.builder()
                                        .put("public.value", "public")
                                        .put("Public.value", "case-sensitive")
                                        .build()),
                                argumentSet(
                                    "exclusion wins, declarative="
                                        + declarativeConfig
                                        + ", preview="
                                        + preview,
                                    declarativeConfig,
                                    preview,
                                    "*.value",
                                    "private.*,Public.*",
                                    Attributes.builder().put("public.value", "public").build()),
                                argumentSet(
                                    "exclude all, declarative="
                                        + declarativeConfig
                                        + ", preview="
                                        + preview,
                                    declarativeConfig,
                                    preview,
                                    null,
                                    "*",
                                    Attributes.empty()))));
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void commonSelectorIgnoresSourceSpecificProperties(boolean declarativeConfig, boolean preview) {
    Map<String, Object> properties = configuration(declarativeConfig, preview);
    putList(properties, declarativeConfig, "common.logging.structured-attributes.included", "*");
    putSourceSpecificProperties(properties, declarativeConfig, "not-a-boolean", "*");

    LoggerContext context = loggerContext();
    OpenTelemetryAppender appender = appender(context);
    initialize(properties, appender);
    emitStructuredLogs(appender, context);

    assertThat(testing.logRecords())
        .extracting(LogRecordData::getAttributes)
        .containsExactly(ALL_ATTRIBUTES, ALL_ATTRIBUTES);
    assertThat(context.getStatusManager().getCopyOfStatusList()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void previewIgnoresSourceSpecificPropertiesWithEmptyCommonSelector(boolean declarativeConfig) {
    Map<String, Object> properties = configuration(declarativeConfig, true);
    putList(properties, declarativeConfig, "common.logging.structured-attributes.included", "");
    putSourceSpecificProperties(properties, declarativeConfig, "not-a-boolean", "*");

    LoggerContext context = loggerContext();
    OpenTelemetryAppender appender = appender(context);
    initialize(properties, appender);
    emitStructuredLogs(appender, context);

    assertThat(testing.logRecords())
        .extracting(LogRecordData::getAttributes)
        .containsExactly(ALL_ATTRIBUTES, ALL_ATTRIBUTES);
    assertThat(context.getStatusManager().getCopyOfStatusList()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyCommonSelectorPreservesSourceSpecificPropertiesOutsidePreview(
      boolean declarativeConfig) {
    Map<String, Object> properties = configuration(declarativeConfig, false);
    putList(properties, declarativeConfig, "common.logging.structured-attributes.included", "");
    putSourceSpecificProperties(properties, declarativeConfig, "true", "private.*,Public.*");

    LoggerContext context = loggerContext();
    OpenTelemetryAppender appender = appender(context);
    initialize(properties, appender);
    emitStructuredLogs(appender, context);

    Attributes expected = Attributes.builder().put("public.value", "public").build();
    assertThat(testing.logRecords())
        .extracting(LogRecordData::getAttributes)
        .containsExactly(expected, expected);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedCaptureBooleansRemainFallbackOutsidePreview(boolean declarativeConfig) {
    Map<String, Object> properties = configuration(declarativeConfig, false);
    putSourceSpecificProperties(properties, declarativeConfig, "true", null);

    LoggerContext context = loggerContext();
    OpenTelemetryAppender appender = appender(context);
    initialize(properties, appender);
    emitStructuredLogs(appender, context);

    assertThat(testing.logRecords())
        .extracting(LogRecordData::getAttributes)
        .containsExactly(ALL_ATTRIBUTES, ALL_ATTRIBUTES);
  }

  @ParameterizedTest
  @CsvSource({
    "false,false,false",
    "false,false,true",
    "false,true,false",
    "true,false,false",
    "true,false,true",
    "true,true,false"
  })
  void commonSelectorOverridesOldXmlSettings(
      boolean declarativeConfig, boolean preview, boolean configureSelector) throws JoranException {
    LoggerContext context = loggerContext();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    configurator.doConfigure(getClass().getResource("/logback-structured-attributes.xml"));
    OpenTelemetryAppender appender =
        (OpenTelemetryAppender) context.getLogger("ROOT").getAppender("OpenTelemetry");
    appender.stop();
    appender.setOpenTelemetry(testing.getOpenTelemetry());

    Map<String, Object> properties = configuration(declarativeConfig, preview);
    if (configureSelector) {
      putList(properties, declarativeConfig, "common.logging.structured-attributes.included", "*");
    }
    initialize(properties, appender);
    emitStructuredLogs(appender, context);

    Attributes expected =
        preview || configureSelector
            ? ALL_ATTRIBUTES
            : Attributes.builder().put("public.value", "public").build();
    assertThat(testing.logRecords())
        .extracting(LogRecordData::getAttributes)
        .containsExactly(expected, expected);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void commonSelectorDoesNotFilterOptedInAmbientAttributes(boolean declarativeConfig) {
    Map<String, Object> properties = configuration(declarativeConfig, true);
    putList(properties, declarativeConfig, "common.logging.structured-attributes.excluded", "*");
    putList(
        properties,
        declarativeConfig,
        "logback-appender.experimental.mdc-attributes.included",
        "ambient.*");
    putList(
        properties,
        declarativeConfig,
        "logback-appender.experimental.logger-context-attributes.included",
        "ambient.*");

    LoggerContext context = loggerContext();
    OpenTelemetryAppender appender = appender(context);
    initialize(properties, appender);
    emitStructuredLogs(appender, context);

    Attributes expected =
        Attributes.builder().put("ambient.mdc", "mdc").put("ambient.context", "context").build();
    assertThat(testing.logRecords())
        .extracting(LogRecordData::getAttributes)
        .containsExactly(expected, expected);
  }

  private static Map<String, Object> configuration(boolean declarativeConfig, boolean preview) {
    Map<String, Object> properties = new HashMap<>();
    if (declarativeConfig) {
      properties.put("otel.file_format", "1.1");
    }
    properties.put(propertyName(declarativeConfig, "common.v3-preview"), preview);
    return properties;
  }

  private static void putSourceSpecificProperties(
      Map<String, Object> properties, boolean declarativeConfig, String capture, String excluded) {
    for (String source :
        new String[] {
          "key-value-pair-attributes",
          "logstash-marker-attributes",
          "logstash-structured-argument-attributes"
        }) {
      putList(
          properties,
          declarativeConfig,
          "logback-appender.experimental." + source + ".excluded",
          excluded);
      String captureProperty =
          source.equals("logstash-structured-argument-attributes")
              ? "capture-logstash-structured-arguments"
              : "capture-" + source;
      properties.put(
          propertyName(declarativeConfig, "logback-appender.experimental." + captureProperty),
          capture);
    }
  }

  private static void putList(
      Map<String, Object> properties, boolean declarativeConfig, String property, String value) {
    if (value == null) {
      return;
    }
    String name = propertyName(declarativeConfig, property);
    if (declarativeConfig) {
      String[] values = value.split(",", -1);
      for (int i = 0; i < values.length; i++) {
        properties.put(name + "[" + i + "]", values[i]);
      }
    } else {
      properties.put(name, value);
    }
  }

  private static String propertyName(boolean declarativeConfig, String property) {
    if (!declarativeConfig) {
      return "otel.instrumentation." + property;
    }
    String name = property.replace('-', '_');
    if (name.startsWith("logback_appender.experimental.")) {
      name = name.replace("logback_appender.experimental.", "logback_appender.");
      int separator = name.indexOf('.', "logback_appender.".length());
      name =
          separator < 0
              ? name + "/development"
              : name.substring(0, separator) + "/development" + name.substring(separator);
    }
    return "otel.instrumentation/development.java." + name;
  }

  private static LoggerContext loggerContext() {
    LoggerContext context = new LoggerContext();
    context.putProperty("ambient.context", "context");
    cleanup.deferCleanup(context::stop);
    return context;
  }

  private static OpenTelemetryAppender appender(LoggerContext context) {
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.setContext(context);
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    cleanup.deferCleanup(appender::stop);
    return appender;
  }

  private static void initialize(Map<String, Object> properties, OpenTelemetryAppender appender) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    LogbackAppenderInstaller.initializeStructuredAttributesFromProperties(environment, appender);
    LogbackAppenderInstaller.initializeMdcAttributesFromProperties(environment, appender);
    LogbackAppenderInstaller.initializeLoggerContextAttributesFromProperties(environment, appender);
    appender.start();
  }

  private static void emitStructuredLogs(OpenTelemetryAppender appender, LoggerContext context) {
    Map<String, String> values =
        Map.of(
            "public.value", "public", "private.value", "private", "Public.value", "case-sensitive");
    LoggingEvent markers = event(context);
    markers.addMarker(Markers.appendEntries(values));
    appender.doAppend(markers);

    LoggingEvent arguments = event(context);
    arguments.setArgumentArray(new Object[] {StructuredArguments.entries(values)});
    appender.doAppend(arguments);
  }

  private static LoggingEvent event(LoggerContext context) {
    LoggingEvent event =
        new LoggingEvent(
            LogbackStructuredAttributesTest.class.getName(),
            context.getLogger("test"),
            Level.INFO,
            "structured event",
            null,
            null);
    event.setMDCPropertyMap(Map.of("ambient.mdc", "mdc"));
    return event;
  }
}
