/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.log4j.contextdata.ListAppender
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.apache.logging.log4j.LogManager

class Log4j27Test extends Log4j2Test implements AgentTestTrait {

  def "resource attributes"() {
    given:
    def logger = LogManager.getLogger("TestLogger")

    when:
    logger.info("log message 1")

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 1
    events[0].message == "log message 1"
    events[0].contextData["trace_id"] == null
    events[0].contextData["span_id"] == null
    events[0].contextData["service.name"] == "unknown_service:java"
    events[0].contextData["telemetry.sdk.language"] == "java"
  }
}
