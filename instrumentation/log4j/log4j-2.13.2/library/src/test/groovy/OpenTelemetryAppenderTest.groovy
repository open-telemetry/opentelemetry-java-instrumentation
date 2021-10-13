/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.log4j.v2_13_2.InMemoryLogExporter
import io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryAppender
import io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryLog4j
import io.opentelemetry.instrumentation.log4j.v2_13_2.SimpleLogProcessor
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.logging.LogSinkSdkProvider
import io.opentelemetry.sdk.resources.Resource
import org.apache.logging.log4j.LogManager

class OpenTelemetryAppenderTest extends InstrumentationSpecification implements LibraryTestTrait {

  private static InMemoryLogExporter logExporter
  private static Resource resource
  private static InstrumentationLibraryInfo instrumentationLibraryInfo
  private static Attributes attributes

  def setupSpec() {
    logExporter = InMemoryLogExporter.create()
    resource = Resource.getDefault()
    instrumentationLibraryInfo = InstrumentationLibraryInfo.create(OpenTelemetryAppender.getName(), null)
    attributes = Attributes.builder().put("logger.name", "TestLogger").put("thread.name", "Time-limited test").build()

    def sdkSinkProvider = LogSinkSdkProvider.builder().build()
    sdkSinkProvider.addLogProcessor(SimpleLogProcessor.create(logExporter))
    def logSink = sdkSinkProvider.get(null, null)
    OpenTelemetryLog4j.initialize(logSink, resource)
  }

  def cleanup() {
    logExporter.reset()
  }

  def "no span"() {
    given:
    def logger = LogManager.getLogger("TestLogger")

    when:
    logger.info("log message 1")
    logger.info("log message 2")

    def logRecords = logExporter.getFinishedLogItems()

    then:
    logRecords.size() == 2
    logRecords[0].getBody().asString() == "log message 1"
    logRecords[0].getResource() == resource
    logRecords[0].getInstrumentationLibraryInfo() == instrumentationLibraryInfo
    logRecords[0].getTraceId() == ""
    logRecords[0].getSpanId() == ""
    logRecords[0].getAttributes() == attributes

    logRecords[1].getBody().asString() == "log message 2"
    logRecords[1].getResource() == resource
    logRecords[1].getInstrumentationLibraryInfo() == instrumentationLibraryInfo
    logRecords[1].getTraceId() == ""
    logRecords[1].getSpanId() == ""
    logRecords[1].getAttributes() == attributes
  }

  def "with span"() {
    given:
    def logger = LogManager.getLogger("TestLogger")

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

    def logRecords = logExporter.getFinishedLogItems()

    then:
    logRecords.size() == 3
    logRecords[0].getBody().asString() == "log message 1"
    logRecords[0].getResource() == resource
    logRecords[0].getInstrumentationLibraryInfo() == instrumentationLibraryInfo
    logRecords[0].getTraceId() == span1.spanContext.traceId
    logRecords[0].getSpanId() == span1.spanContext.spanId
    logRecords[0].getAttributes() == attributes.toBuilder().put("trace_flags", "01").build()

    logRecords[1].getBody().asString() == "log message 2"
    logRecords[1].getResource() == resource
    logRecords[1].getInstrumentationLibraryInfo() == instrumentationLibraryInfo
    logRecords[1].getTraceId() == ""
    logRecords[1].getSpanId() == ""
    logRecords[1].getAttributes() == attributes

    logRecords[2].getBody().asString() == "log message 3"
    logRecords[2].getResource() == resource
    logRecords[2].getInstrumentationLibraryInfo() == instrumentationLibraryInfo
    logRecords[2].getTraceId() == span2.spanContext.traceId
    logRecords[2].getSpanId() == span2.spanContext.spanId
    logRecords[2].getAttributes() == attributes.toBuilder().put("trace_flags", "01").build()
  }

}
