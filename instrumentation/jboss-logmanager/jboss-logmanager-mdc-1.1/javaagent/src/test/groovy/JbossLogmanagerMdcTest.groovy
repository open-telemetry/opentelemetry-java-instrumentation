/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.jboss.logmanager.ExtLogRecord
import org.jboss.logmanager.Level
import org.jboss.logmanager.LogContext

import java.util.logging.Handler
import java.util.logging.LogRecord

class JbossLogmanagerMdcTest extends AgentInstrumentationSpecification {
  class LogHandler extends Handler {
    public List<ExtLogRecord> logRecords

    LogHandler(LinkedList<ExtLogRecord> logRecords) {
      this.logRecords = logRecords
    }
    @Override
    void publish(LogRecord record) {
      logRecords.push(record as ExtLogRecord)
    }

    @Override
    void flush() {

    }

    @Override
    void close() throws SecurityException {
    }
  }
  def "no ids when no span"() {
    given:
    LinkedList<ExtLogRecord> logRecords = []
    def logger = LogContext.getLogContext().getLogger('TestLogger')
    logger.setLevel(Level.INFO)
    logger.addHandler(new LogHandler(logRecords))

    when:
    logger.info("log message 1")

    then:
    logRecords.size() == 1
    logRecords.get(0).message == "log message 1"
    logRecords.get(0).getMdc("trace_id") == null
    logRecords.get(0).getMdc("span_id") == null
    logRecords.get(0).getMdc("trace_flags") == null

    cleanup:
    logRecords.clear()
  }

  def "ids when span"() {
    given:
    def logger = LogContext.getLogContext().getLogger('TestLogger')
    logger.setLevel(Level.DEBUG)
    LinkedList<ExtLogRecord> logRecords = []
    logger.addHandler(new LogHandler(logRecords))
    when:
    def span1 = runWithSpan("test") {
      logger.info("log message 1")
      Span.current()
    }

    logger.info("log message 2")

    def span2 = runWithSpan("test 2") {
      logger.info("log message 3")
      Span.current()
    }

    then:

    logRecords.size() == 3

    // The method getMdcCopy exists only in jboss-logmanager 1.3+
    def hasGetMdcCopy = logRecords.get(0).metaClass.getMetaMethod("getMdcCopy") != null

    logRecords.get(2).message == "log message 1"
    logRecords.get(2).getMdc("trace_id") == span1.spanContext.traceId
    logRecords.get(2).getMdc("span_id") == span1.spanContext.spanId
    logRecords.get(2).getMdc("trace_flags") == "01"

    if (hasGetMdcCopy) {
      assert logRecords.get(2).getMdcCopy().get("trace_id") == span1.spanContext.traceId
      assert logRecords.get(2).getMdcCopy().get("span_id") == span1.spanContext.spanId
      assert logRecords.get(2).getMdcCopy().get("trace_flags") == "01"
    }

    logRecords.get(1).message == "log message 2"
    logRecords.get(1).getMdc("trace_id") == null
    logRecords.get(1).getMdc("span_id") == null
    logRecords.get(1).getMdc("trace_flags") == null

    logRecords.get(0).message == "log message 3"
    logRecords.get(0).getMdc("trace_id") == span2.spanContext.traceId
    logRecords.get(0).getMdc("span_id") == span2.spanContext.spanId
    logRecords.get(0).getMdc("trace_flags") == "01"

    if (hasGetMdcCopy) {
      assert logRecords.get(0).getMdcCopy().get("trace_id") == span2.spanContext.traceId
      assert logRecords.get(0).getMdcCopy().get("span_id") == span2.spanContext.spanId
      assert logRecords.get(0).getMdcCopy().get("trace_flags") == "01"
    }

    cleanup:
    logRecords.clear()
  }
}
