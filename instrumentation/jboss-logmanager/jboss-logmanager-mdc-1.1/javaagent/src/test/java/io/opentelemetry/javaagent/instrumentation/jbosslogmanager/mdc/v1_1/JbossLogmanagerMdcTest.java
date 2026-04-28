/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.mdc.v1_1;

import static org.assertj.core.api.Assertions.assertThat;

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

      assertThat(logRecords).extracting(ExtLogRecord::getMessage).containsExactly("log message 1");

      ExtLogRecord logRecord = logRecords.get(0);
      assertThat(logRecord.getMdc("trace_id")).isNull();
      assertThat(logRecord.getMdc("span_id")).isNull();
      assertThat(logRecord.getMdc("trace_flags")).isNull();
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

      assertThat(logRecords)
          .extracting(ExtLogRecord::getMessage)
          .containsExactly("log message 1", "log message 2", "log message 3");

      ExtLogRecord firstLogRecord = logRecords.get(0);
      ExtLogRecord secondLogRecord = logRecords.get(1);
      ExtLogRecord thirdLogRecord = logRecords.get(2);

      Method getMdcCopy = null;
      try {
        getMdcCopy = firstLogRecord.getClass().getMethod("getMdcCopy");
      } catch (NoSuchMethodException ignored) {
        // ignored
      }

      assertThat(firstLogRecord.getMdc("trace_id")).isEqualTo(span1.getSpanContext().getTraceId());
      assertThat(firstLogRecord.getMdc("span_id")).isEqualTo(span1.getSpanContext().getSpanId());
      assertThat(firstLogRecord.getMdc("trace_flags"))
          .isEqualTo(span1.getSpanContext().getTraceFlags().asHex());

      if (getMdcCopy != null) {
        @SuppressWarnings("unchecked")
        Map<String, String> copiedMdc = (Map<String, String>) getMdcCopy.invoke(firstLogRecord);
        assertThat(copiedMdc.get("trace_id")).isEqualTo(span1.getSpanContext().getTraceId());
        assertThat(copiedMdc.get("span_id")).isEqualTo(span1.getSpanContext().getSpanId());
        assertThat(copiedMdc.get("trace_flags"))
            .isEqualTo(span1.getSpanContext().getTraceFlags().asHex());
      }

      assertThat(secondLogRecord.getMdc("trace_id")).isNull();
      assertThat(secondLogRecord.getMdc("span_id")).isNull();
      assertThat(secondLogRecord.getMdc("trace_flags")).isNull();

      assertThat(thirdLogRecord.getMdc("trace_id")).isEqualTo(span2.getSpanContext().getTraceId());
      assertThat(thirdLogRecord.getMdc("span_id")).isEqualTo(span2.getSpanContext().getSpanId());
      assertThat(thirdLogRecord.getMdc("trace_flags"))
          .isEqualTo(span2.getSpanContext().getTraceFlags().asHex());

      if (getMdcCopy != null) {
        @SuppressWarnings("unchecked")
        Map<String, String> copiedMdc = (Map<String, String>) getMdcCopy.invoke(thirdLogRecord);
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
