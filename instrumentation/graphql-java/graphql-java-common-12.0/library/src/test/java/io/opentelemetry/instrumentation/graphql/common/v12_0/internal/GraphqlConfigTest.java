/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GraphqlConfigTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void replacementSettingTakesPrecedence(boolean enabled) {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("operation_name_in_span_name").getBoolean("enabled")).thenReturn(enabled);
    when(config.get("add_operation_name_to_span_name").getBoolean("enabled")).thenReturn(!enabled);

    Logger logger = Logger.getLogger(GraphqlConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(GraphqlConfig.getOperationNameInSpanNameEnabled(config)).isEqualTo(enabled);
      assertThat(handler.records).isEmpty();
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void deprecatedSettingIsUsedAsFallback(boolean enabled) {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("operation_name_in_span_name").getBoolean("enabled")).thenReturn(null);
    when(config.get("add_operation_name_to_span_name").getBoolean("enabled")).thenReturn(enabled);

    Logger logger = Logger.getLogger(GraphqlConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(GraphqlConfig.getOperationNameInSpanNameEnabled(config)).isEqualTo(enabled);
      assertThat(handler.records)
          .singleElement()
          .extracting(LogRecord::getMessage)
          .isEqualTo(
              "The otel.instrumentation.graphql.add-operation-name-to-span-name.enabled setting is"
                  + " deprecated and will be removed in 3.0. Use "
                  + "otel.instrumentation.graphql.operation-name-in-span-name.enabled instead.");
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
