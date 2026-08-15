/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the deprecated boolean settings of the appender are still configurable from {@code
 * logback.xml}. Joran converts the element text with {@code Boolean.valueOf}, so these settings
 * remain configurable through their primitive {@code boolean} setters.
 */
class OpenTelemetryAppenderXmlConfigurationTest {

  @Test
  void deprecatedKeyValuePairSettingIsAppliedFromXml() throws JoranException {
    assertThat(
            deprecationWarnings(
                "<captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>",
                "capture-key-value-pair-attributes"))
        .hasSize(1);
  }

  @Test
  void deprecatedKeyValuePairSettingIsAbsentByDefault() throws JoranException {
    assertThat(deprecationWarnings("", "capture-key-value-pair-attributes")).isEmpty();
  }

  @Test
  void deprecatedLoggerContextSettingIsAppliedFromXml() throws JoranException {
    assertThat(
            deprecationWarnings(
                "<captureLoggerContext>true</captureLoggerContext>",
                "capture-logger-context-attributes"))
        .hasSize(1);
  }

  @Test
  void deprecatedLoggerContextSettingIsAbsentByDefault() throws JoranException {
    assertThat(deprecationWarnings("", "capture-logger-context-attributes")).isEmpty();
  }

  @Test
  void deprecatedLogstashMarkerSettingIsAppliedFromXml() throws JoranException {
    assertThat(
            deprecationWarnings(
                "<captureLogstashMarkerAttributes>true</captureLogstashMarkerAttributes>",
                "capture-logstash-marker-attributes"))
        .hasSize(1);
  }

  @Test
  void deprecatedLogstashMarkerSettingIsAbsentByDefault() throws JoranException {
    assertThat(deprecationWarnings("", "capture-logstash-marker-attributes")).isEmpty();
  }

  /**
   * Configures an appender declaring {@code settings}, starts it, and returns the warnings it
   * reported that name {@code deprecatedProperty}.
   */
  private static List<Status> deprecationWarnings(String settings, String deprecatedProperty)
      throws JoranException {
    // the appender is started by Joran while the configuration is applied, but only when it is
    // referenced, because logback 1.3 and later skip appenders that no logger refers to
    String configuration =
        "<configuration><appender name='OTEL' class='"
            + OpenTelemetryAppender.class.getName()
            + "'>"
            + settings
            + "</appender><root level='INFO'><appender-ref ref='OTEL'/></root></configuration>";

    LoggerContext loggerContext = new LoggerContext();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(loggerContext);
    configurator.doConfigure(new ByteArrayInputStream(configuration.getBytes(UTF_8)));

    return loggerContext.getStatusManager().getCopyOfStatusList().stream()
        .filter(
            status ->
                status.getMessage() != null && status.getMessage().contains(deprecatedProperty))
        .collect(toList());
  }
}
