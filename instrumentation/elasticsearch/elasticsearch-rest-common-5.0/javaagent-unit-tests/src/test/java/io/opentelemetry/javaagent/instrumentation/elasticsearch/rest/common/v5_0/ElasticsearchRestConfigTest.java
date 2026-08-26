/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.bootstrap.elasticsearch.ElasticsearchConfigAccess;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElasticsearchRestConfigTest {

  @BeforeEach
  void resetWarning() throws ReflectiveOperationException {
    Field field =
        ElasticsearchConfigAccess.class.getDeclaredField("captureSearchQueryWarningLogged");
    field.setAccessible(true);
    ((AtomicBoolean) field.get(null)).set(false);
  }

  @Test
  void explicitConfigIsAppliedAndWarnsOnce() {
    DeclarativeConfigProperties enabled = mock(DeclarativeConfigProperties.class);
    when(enabled.getBoolean("capture_search_query")).thenReturn(true);
    DeclarativeConfigProperties disabled = mock(DeclarativeConfigProperties.class);
    when(disabled.getBoolean("capture_search_query")).thenReturn(false);
    Logger logger = Logger.getLogger(ElasticsearchRestConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(ElasticsearchRestConfig.captureSearchQuery(enabled, false)).isTrue();
      assertThat(ElasticsearchRestConfig.captureSearchQuery(disabled, false)).isFalse();

      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.elasticsearch.capture-search-query setting and the"
                  + " equivalent declarative configuration property are deprecated and will be"
                  + " removed in 3.0. In 3.0, sanitized search query bodies are always captured and"
                  + " there is no replacement setting.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void absentConfigPreservesDefault() {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);

    assertThat(ElasticsearchRestConfig.captureSearchQuery(config, false)).isFalse();
  }

  @Test
  void v3PreviewCapturesWithoutReadingDeprecatedConfig() {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);

    assertThat(ElasticsearchRestConfig.captureSearchQuery(config, true)).isTrue();
    verify(config, never()).getBoolean("capture_search_query");
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
