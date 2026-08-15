/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.logstash.logback.argument.StructuredArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogbackLogstashStructuredArgsSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("logstash-structured-args-selector");

  private static final Map<String, String> STRUCTURED_ARGUMENTS = structuredArguments();

  @Test
  void capturesConfiguredLogstashStructuredArgumentAttributes() {
    logger.info(
        "selector test {} {} {}",
        StructuredArguments.keyValue("key1", "key1-value"),
        StructuredArguments.keyValue("key2", "key2-value"),
        StructuredArguments.keyValue("other", "other-value"));

    testing.waitAndAssertLogRecords(
        logRecord -> {
          Attributes attributes = logRecord.actual().getAttributes();
          assertThat(capturedStructuredArguments(attributes))
              .containsExactlyInAnyOrderEntriesOf(expectedStructuredArguments());
        });
  }

  private static Map<String, String> structuredArguments() {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("key1", "key1-value");
    attributes.put("key2", "key2-value");
    attributes.put("other", "other-value");
    return attributes;
  }

  private static Map<String, String> capturedStructuredArguments(Attributes attributes) {
    Map<String, String> captured = new HashMap<>();
    STRUCTURED_ARGUMENTS.forEach(
        (key, value) -> {
          String attributeValue = attributes.get(stringKey(key));
          if (attributeValue != null) {
            captured.put(key, attributeValue);
          }
        });
    return captured;
  }

  private static Map<String, String> expectedStructuredArguments() {
    List<String> expectedKeys;
    switch (System.getProperty("testLogstashStructuredArgsConfiguration", "new")) {
      case "legacy":
        // the deprecated setting captures every structured argument
        return new HashMap<>(STRUCTURED_ARGUMENTS);
      case "precedence":
        expectedKeys = singletonList("key1");
        break;
      case "exclude-only":
        // an empty included list captures everything not excluded
        expectedKeys = asList("key1", "key2");
        break;
      default:
        // the default test task configures
        // logstash-structured-argument-attributes.included=key?
        expectedKeys = asList("key1", "key2");
        break;
    }
    Map<String, String> expected = new HashMap<>();
    expectedKeys.forEach(key -> expected.put(key, STRUCTURED_ARGUMENTS.get(key)));
    return expected;
  }
}
