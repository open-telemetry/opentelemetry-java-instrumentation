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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LogbackMdcSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("selector");

  private static final Map<String, String> MDC_ENTRIES = mdcEntries();

  @Test
  void capturesConfiguredMdcAttributes() {
    MDC_ENTRIES.forEach(MDC::put);
    MDC.put("otel.event.name", "MyEventName");
    try {
      logger.info("selector test");
    } finally {
      MDC.clear();
    }

    testing.waitAndAssertLogRecords(
        logRecord -> {
          logRecord.hasEventName("MyEventName");
          Attributes attributes = logRecord.actual().getAttributes();
          assertThat(attributes.get(stringKey("otel.event.name"))).isNull();
          assertThat(capturedMdcAttributes(attributes))
              .containsExactlyInAnyOrderEntriesOf(expectedMdcAttributes());
        });
  }

  private static Map<String, String> mdcEntries() {
    Map<String, String> entries = new HashMap<>();
    entries.put("key1", "key1-value");
    entries.put("keyLong", "key-long-value");
    entries.put("request-id", "request-id-value");
    entries.put("request-secret", "request-secret-value");
    entries.put("user-a", "user-a-value");
    entries.put("legacy", "legacy-value");
    entries.put("new", "new-value");
    entries.put("*", "star-value");
    return entries;
  }

  private static Map<String, String> capturedMdcAttributes(Attributes attributes) {
    Map<String, String> captured = new HashMap<>();
    MDC_ENTRIES.forEach(
        (key, value) -> {
          String attributeValue = attributes.get(stringKey(key));
          if (attributeValue != null) {
            captured.put(key, attributeValue);
          }
        });
    return captured;
  }

  private static Map<String, String> expectedMdcAttributes() {
    List<String> expectedKeys;
    switch (System.getProperty("testMdcConfiguration", "new")) {
      case "legacy":
        // the deprecated setting matches keys literally, so "*" alongside another entry captures
        // only the MDC key that is literally named "*"
        expectedKeys = asList("*", "legacy");
        break;
      case "legacy-all":
        // a deprecated list whose only entry is "*" still captures every MDC key
        return new HashMap<>(MDC_ENTRIES);
      case "precedence":
        expectedKeys = singletonList("new");
        break;
      case "exclude-only":
        expectedKeys = asList("key1", "keyLong", "request-id", "user-a", "legacy", "new", "*");
        break;
      default:
        // the test task configures mdc-attributes.included=key?
        expectedKeys = singletonList("key1");
        break;
    }
    Map<String, String> expected = new HashMap<>();
    expectedKeys.forEach(key -> expected.put(key, MDC_ENTRIES.get(key)));
    return expected;
  }
}
