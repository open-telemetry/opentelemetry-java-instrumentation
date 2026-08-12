/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagingConfigTest {

  @AfterEach
  void clearWarnings() throws Exception {
    Field field = MessagingConfig.class.getDeclaredField("warnedDeprecatedProperties");
    field.setAccessible(true);
    ((Set<?>) field.get(null)).clear();
  }

  @Test
  void readsNewSelector() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("headers/development").getScalarList("included", String.class))
        .thenReturn(asList("Test-*", "other"));
    when(config.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("*-secret"));

    IncludeExclude headers = MessagingConfig.getHeaders(config, false, false);

    assertThat(headers.getIncluded()).containsExactly("Test-*", "other");
    assertThat(headers.getExcluded()).containsExactly("*-secret");
  }

  @Test
  void readsExcludeOnlySelector() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("secret"));

    IncludeExclude headers = MessagingConfig.getHeaders(config, false, false);

    assertThat(headers.getIncluded()).isEmpty();
    assertThat(headers.getExcluded()).containsExactly("secret");
  }

  @Test
  void newSelectorTakesPrecedenceOverDeprecatedConfig() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("headers/development").getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));

    IncludeExclude headers = MessagingConfig.getHeaders(config, false, false);

    assertThat(headers.getIncluded()).containsExactly("new");
  }

  @Test
  void deprecatedConfigIsIncludeOnlyFallbackAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));
    Logger logger = Logger.getLogger(MessagingConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      IncludeExclude first = MessagingConfig.getHeaders(config, false, false);
      IncludeExclude second = MessagingConfig.getHeaders(config, false, false);

      assertThat(first.getIncluded()).containsExactly("deprecated");
      assertThat(first.getExcluded()).isEmpty();
      assertThat(second).isEqualTo(first);
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.messaging.experimental.capture-headers setting and the"
                  + " equivalent declarative configuration property are deprecated and will be"
                  + " removed in 3.0. Use"
                  + " otel.instrumentation.messaging.experimental.headers.included or equivalent"
                  + " declarative configuration instead.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void emptyDeprecatedConfigCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_headers/development", String.class)).thenReturn(emptyList());

    assertThat(MessagingConfig.getHeaders(config, false, false).isEmpty()).isTrue();
  }

  @Test
  void absentSelectorCapturesNothing() {
    assertThat(MessagingConfig.getHeaders(mockConfig(), false, false).isEmpty()).isTrue();
  }

  @Test
  void emptySelectorCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("headers/development").getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(config.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(emptyList());

    assertThat(MessagingConfig.getHeaders(config, false, false).isEmpty()).isTrue();
  }

  @Test
  void deprecatedConfigIsIgnoredInV3Preview() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));

    assertThat(MessagingConfig.getHeaders(config, true, false).isEmpty()).isTrue();
  }

  @Test
  void systemPropertyFallbackIsOnlyUsedWhenEnabled() {
    DeclarativeConfigProperties config = mockConfig();
    System.setProperty("otel.instrumentation.messaging.experimental.headers.included", "from-prop");
    try {
      assertThat(MessagingConfig.getHeaders(config, false, false).isEmpty()).isTrue();
      assertThat(MessagingConfig.getHeaders(config, false, true).getIncluded())
          .containsExactly("from-prop");
    } finally {
      System.clearProperty("otel.instrumentation.messaging.experimental.headers.included");
    }
  }

  @Test
  void createUsesV3PreviewFromOpenTelemetryInstance() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.getBoolean("v3_preview")).thenReturn(true);
    when(commonConfig.get("messaging").getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).isEmpty()).isTrue();
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("headers/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(config.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(config.getScalarList("capture_headers/development", String.class)).thenReturn(null);
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
