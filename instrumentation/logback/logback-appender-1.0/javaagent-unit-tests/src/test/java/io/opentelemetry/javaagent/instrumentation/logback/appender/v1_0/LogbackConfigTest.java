/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LogbackConfigTest {

  @AfterEach
  void clearWarning() throws ReflectiveOperationException {
    Field field = LogbackConfig.class.getDeclaredField("warnedDeprecatedProperty");
    field.setAccessible(true);
    ((AtomicBoolean) field.get(null)).set(false);
  }

  @Test
  void readsExcludedOnlySelector() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("*-secret"));

    LogbackConfig logbackConfig = new LogbackConfig(config, false);

    IncludeExclude mdcAttributes = logbackConfig.getMdcAttributes();
    assertThat(mdcAttributes).isNotNull();
    assertThat(mdcAttributes.getIncluded()).isEmpty();
    assertThat(mdcAttributes.getExcluded()).containsExactly("*-secret");
  }

  @Test
  void newSelectorTakesPrecedenceOverDeprecatedConfig() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("deprecated"));

    LogbackConfig logbackConfig = new LogbackConfig(config, false);

    assertThat(logbackConfig.getMdcAttributes().getIncluded()).containsExactly("new");
  }

  @Test
  void deprecatedConfigIsIgnoredInV3Preview() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("deprecated"));

    LogbackConfig logbackConfig = new LogbackConfig(config, true);

    assertThat(logbackConfig.getMdcAttributes()).isNull();
  }

  @Test
  void deprecatedConfigIsIncludeOnlyFallbackAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("deprecated"));
    Logger logger = Logger.getLogger(LogbackConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      LogbackConfig first = new LogbackConfig(config, false);
      LogbackConfig second = new LogbackConfig(config, false);

      assertThat(first.getMdcAttributes().getIncluded()).containsExactly("deprecated");
      assertThat(first.getMdcAttributes().getExcluded()).isEmpty();
      assertThat(second.getMdcAttributes()).isEqualTo(first.getMdcAttributes());
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.logback-appender.experimental.capture-mdc-attributes"
                  + " setting and the equivalent declarative configuration property are"
                  + " deprecated and will be removed in 3.0. Use"
                  + " otel.instrumentation.logback-appender.experimental.mdc-attributes.included"
                  + " or equivalent declarative configuration instead.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(config.get("mdc_attributes/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(config.getScalarList("capture_mdc_attributes/development", String.class)).thenReturn(null);
    return config;
  }

  private static final class TestHandler extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
