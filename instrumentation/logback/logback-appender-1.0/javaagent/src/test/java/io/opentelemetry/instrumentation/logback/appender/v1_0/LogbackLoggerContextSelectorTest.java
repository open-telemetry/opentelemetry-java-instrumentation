/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogbackLoggerContextSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("logger-context-selector");

  private static final Map<String, String> LOGGER_CONTEXT_PROPERTIES = loggerContextProperties();

  @Test
  void capturesConfiguredLoggerContextAttributes() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    LOGGER_CONTEXT_PROPERTIES.forEach(loggerContext::putProperty);
    try {
      logger.info("selector test");
    } finally {
      LOGGER_CONTEXT_PROPERTIES.keySet().forEach(key -> loggerContext.putProperty(key, null));
    }

    testing.waitAndAssertLogRecords(
        logRecord -> {
          Attributes attributes = logRecord.actual().getAttributes();
          assertThat(capturedLoggerContextAttributes(attributes))
              .containsExactlyInAnyOrderEntriesOf(expectedLoggerContextAttributes());
        });
  }

  private static Map<String, String> loggerContextProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("key1", "key1-value");
    properties.put("keyLong", "key-long-value");
    properties.put("request-id", "request-id-value");
    properties.put("request-secret", "request-secret-value");
    properties.put("new", "new-value");
    return properties;
  }

  private static Map<String, String> capturedLoggerContextAttributes(Attributes attributes) {
    Map<String, String> captured = new HashMap<>();
    LOGGER_CONTEXT_PROPERTIES.forEach(
        (key, value) -> {
          String attributeValue = attributes.get(stringKey(key));
          if (attributeValue != null) {
            captured.put(key, attributeValue);
          }
        });
    return captured;
  }

  private static Map<String, String> expectedLoggerContextAttributes() {
    List<String> expectedKeys;
    switch (System.getProperty("testLoggerContextConfiguration", "new")) {
      case "legacy":
        // the deprecated setting captures every logger context property
        return new HashMap<>(LOGGER_CONTEXT_PROPERTIES);
      case "precedence":
        expectedKeys = singletonList("new");
        break;
      case "exclude-only":
        expectedKeys = asList("key1", "keyLong", "request-id", "new");
        break;
      default:
        // the test task configures logger-context-attributes.included=key?
        expectedKeys = singletonList("key1");
        break;
    }
    Map<String, String> expected = new HashMap<>();
    expectedKeys.forEach(key -> expected.put(key, LOGGER_CONTEXT_PROPERTIES.get(key)));
    return expected;
  }
}
