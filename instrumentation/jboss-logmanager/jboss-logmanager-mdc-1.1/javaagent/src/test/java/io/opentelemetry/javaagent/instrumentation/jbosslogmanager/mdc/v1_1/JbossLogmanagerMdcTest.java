/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.mdc.v1_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JbossLogmanagerMdcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static class LogHandler extends Handler {
    private final List<ExtLogRecord> logRecords;

    LogHandler(List<ExtLogRecord> logRecords) {
      this.logRecords = logRecords;
    }

    @Override
    public void publish(LogRecord record) {
      logRecords.add((ExtLogRecord) record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }

  @Test
  void noIdsGeneratedWhenNoSpanProvided() {
    Logger logger = LogContext.getLogContext().getLogger("noIdsGeneratedWhenNoSpanProvided");
    LinkedList<ExtLogRecord> logRecords = new LinkedList<>();
    LogHandler handler = new LogHandler(logRecords);

    logger.setLevel(Level.INFO);
    logger.addHandler(handler);
    try {
      logger.info("log message 1");

      assertThat(logRecords).hasSize(1);
      assertThat(logRecords.get(0).getMessage()).isEqualTo("log message 1");
      assertThat(logRecords.get(0).getMdc("trace_id")).isNull();
      assertThat(logRecords.get(0).getMdc("span_id")).isNull();
      assertThat(logRecords.get(0).getMdc("trace_flags")).isNull();
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void idsGeneratedWhenSpanProvided() throws ReflectiveOperationException {
    Logger logger = LogContext.getLogContext().getLogger("idsGeneratedWhenSpanProvided");
    logger.setLevel(Level.DEBUG);
    LinkedList<ExtLogRecord> logRecords = new LinkedList<>();
    LogHandler handler = new LogHandler(logRecords);
    logger.addHandler(handler);
    try {
      Span span1 =
          testing.runWithSpan(
              "test 1",
              () -> {
                logger.info("log message 1");
                return Span.current();
              });

      logger.info("log message 2");

      Span span2 =
          testing.runWithSpan(
              "test 2",
              () -> {
                logger.info("log message 3");
                return Span.current();
              });

      assertThat(logRecords).hasSize(3);

      Method getMdcCopy = null;
      try {
        getMdcCopy = logRecords.get(0).getClass().getMethod("getMdcCopy");
      } catch (NoSuchMethodException ignored) {
        // ignored
      }

      assertThat(logRecords.get(0).getMessage()).isEqualTo("log message 1");
      assertThat(logRecords.get(0).getMdc("trace_id"))
          .isEqualTo(span1.getSpanContext().getTraceId());
      assertThat(logRecords.get(0).getMdc("span_id")).isEqualTo(span1.getSpanContext().getSpanId());
      assertThat(logRecords.get(0).getMdc("trace_flags"))
          .isEqualTo(span1.getSpanContext().getTraceFlags().asHex());

      if (getMdcCopy != null) {
        @SuppressWarnings("unchecked")
        Map<String, String> copiedMdc = (Map<String, String>) getMdcCopy.invoke(logRecords.get(0));
        assertThat(copiedMdc.get("trace_id")).isEqualTo(span1.getSpanContext().getTraceId());
        assertThat(copiedMdc.get("span_id")).isEqualTo(span1.getSpanContext().getSpanId());
        assertThat(copiedMdc.get("trace_flags"))
            .isEqualTo(span1.getSpanContext().getTraceFlags().asHex());
      }

      assertThat(logRecords.get(1).getMessage()).isEqualTo("log message 2");
      assertThat(logRecords.get(1).getMdc("trace_id")).isNull();
      assertThat(logRecords.get(1).getMdc("span_id")).isNull();
      assertThat(logRecords.get(1).getMdc("trace_flags")).isNull();

      assertThat(logRecords.get(2).getMessage()).isEqualTo("log message 3");
      assertThat(logRecords.get(2).getMdc("trace_id"))
          .isEqualTo(span2.getSpanContext().getTraceId());
      assertThat(logRecords.get(2).getMdc("span_id")).isEqualTo(span2.getSpanContext().getSpanId());
      assertThat(logRecords.get(2).getMdc("trace_flags"))
          .isEqualTo(span2.getSpanContext().getTraceFlags().asHex());

      if (getMdcCopy != null) {
        @SuppressWarnings("unchecked")
        Map<String, String> copiedMdc = (Map<String, String>) getMdcCopy.invoke(logRecords.get(2));
        assertThat(copiedMdc.get("trace_id")).isEqualTo(span2.getSpanContext().getTraceId());
        assertThat(copiedMdc.get("span_id")).isEqualTo(span2.getSpanContext().getSpanId());
        assertThat(copiedMdc.get("trace_flags"))
            .isEqualTo(span2.getSpanContext().getTraceFlags().asHex());
      }
    } finally {
      logger.removeHandler(handler);
    }
  }
}
