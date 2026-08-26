/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class GraphqlCaptureQueryConfigTest {

  @Test
  void deprecatedCaptureQuerySetting() {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(config.getBoolean("capture_query")).thenReturn(false);

    Logger logger = Logger.getLogger(GraphqlConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      boolean v3Preview = SemconvStability.v3Preview();
      assertThat(GraphqlConfig.getCaptureQuery(config)).isEqualTo(v3Preview);
      assertThat(GraphqlConfig.getCaptureQuery(config)).isEqualTo(v3Preview);

      if (v3Preview) {
        verify(config, never()).getBoolean("capture_query");
        assertThat(handler.records).isEmpty();
      } else {
        verify(config, times(2)).getBoolean("capture_query");
        assertThat(handler.records)
            .singleElement()
            .extracting(LogRecord::getMessage)
            .isEqualTo(
                "The otel.instrumentation.graphql.capture-query setting or equivalent declarative"
                    + " configuration is deprecated and will be removed in 3.0. GraphQL queries"
                    + " will always be captured in 3.0; there is no replacement.");
      }
    } finally {
      logger.removeHandler(handler);
    }
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
