/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CouchbaseSpanConfigTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void replacementConfigIsAppliedWithoutWarning(boolean enabled) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(config.getBoolean("emit_experimental_telemetry/development")).thenReturn(enabled);
    Logger logger = Logger.getLogger(CouchbaseSpan.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(CouchbaseSpan.captureExperimentalTelemetry(config)).isEqualTo(enabled);
      verify(config, never()).getBoolean("experimental_span_attributes/development");
      assertThat(handler.records).isEmpty();
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void deprecatedConfigIsAppliedAndWarns(boolean enabled) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(config.getBoolean("emit_experimental_telemetry/development")).thenReturn(null);
    when(config.getBoolean("experimental_span_attributes/development")).thenReturn(enabled);
    Logger logger = Logger.getLogger(CouchbaseSpan.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(CouchbaseSpan.captureExperimentalTelemetry(config)).isEqualTo(enabled);
      assertThat(handler.records)
          .singleElement()
          .extracting(LogRecord::getMessage)
          .isEqualTo(
              "The otel.instrumentation.couchbase.experimental-span-attributes setting and the"
                  + " equivalent declarative configuration property are deprecated for Couchbase"
                  + " 3.x and will be removed in the next minor release. Use"
                  + " otel.instrumentation.couchbase.emit-experimental-telemetry or the equivalent"
                  + " declarative configuration instead.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void absentConfigPreservesDefaultWithoutWarning() {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(config.getBoolean("emit_experimental_telemetry/development")).thenReturn(null);
    when(config.getBoolean("experimental_span_attributes/development")).thenReturn(null);
    Logger logger = Logger.getLogger(CouchbaseSpan.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(CouchbaseSpan.captureExperimentalTelemetry(config)).isFalse();
      assertThat(handler.records).isEmpty();
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
