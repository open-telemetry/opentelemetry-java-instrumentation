/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenTelemetryAppenderXmlConfigurationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @CsvSource({
    "none, false",
    "captureExperimentalAttributes, false",
    "captureThreadAttributes, true"
  })
  void threadAttributesUseOnlyCurrentXmlElement(String element, boolean expected)
      throws JoranException {
    String setting = element.equals("none") ? "" : "<" + element + ">true</" + element + ">";
    String configuration =
        "<configuration><appender name='OTEL' class='"
            + OpenTelemetryAppender.class.getName()
            + "'>"
            + setting
            + "</appender><root level='INFO'><appender-ref ref='OTEL'/></root></configuration>";
    LoggerContext context = new LoggerContext();
    try {
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(context);
      configurator.doConfigure(new ByteArrayInputStream(configuration.getBytes(UTF_8)));
      OpenTelemetryAppender appender =
          (OpenTelemetryAppender) context.getLogger("ROOT").getAppender("OTEL");
      appender.setOpenTelemetry(testing.getOpenTelemetry());
      context.getLogger("test").info("test");

      assertThat(testing.logRecords())
          .satisfiesOnlyOnce(record -> {
            if (expected) {
              assertThat(record.getAttributes().get(stringKey("thread.name")))
                  .isEqualTo(Thread.currentThread().getName());
            } else {
              assertThat(record.getAttributes().get(stringKey("thread.name"))).isNull();
            }
          });
    } finally {
      context.stop();
    }
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
        .filter(status ->
            status.getMessage() != null && status.getMessage().contains(deprecatedProperty))
        .collect(toList());
  }
}
