/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.instrumentation.log4j.appender.v2_17.AbstractLog4j2Test.mapMessageKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StringMapMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Log4jMapMessageSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LogManager.getLogger("selector");

  private static final Map<String, String> MAP_MESSAGE_ENTRIES = mapMessageEntries();

  @Test
  void capturesConfiguredMapMessageAttributes() {
    StringMapMessage message = new StringMapMessage();
    MAP_MESSAGE_ENTRIES.forEach(message::put);
    logger.info(message);

    testing.waitAndAssertLogRecords(
        logRecord -> {
          Attributes attributes = logRecord.actual().getAttributes();
          assertThat(capturedMapMessageAttributes(attributes))
              .containsExactlyInAnyOrderEntriesOf(expectedMapMessageAttributes());
        });
  }

  private static Map<String, String> mapMessageEntries() {
    Map<String, String> entries = new HashMap<>();
    entries.put("order-id", "order-id-value");
    entries.put("order-secret", "order-secret-value");
    entries.put("user-1", "single-value");
    entries.put("user-22", "double-value");
    entries.put("other", "other-value");
    return entries;
  }

  private static Map<String, String> capturedMapMessageAttributes(Attributes attributes) {
    Map<String, String> captured = new HashMap<>();
    MAP_MESSAGE_ENTRIES.forEach(
        (key, value) -> {
          String attributeValue = attributes.get(mapMessageKey(key));
          if (attributeValue != null) {
            captured.put(key, attributeValue);
          }
        });
    return captured;
  }

  private static Map<String, String> expectedMapMessageAttributes() {
    List<String> expectedKeys;
    switch (System.getProperty("testMapMessageConfiguration", "new")) {
      case "legacy":
        // the deprecated boolean captures every attribute
        expectedKeys = asList("order-id", "order-secret", "user-1", "user-22", "other");
        break;
      case "precedence":
        expectedKeys = singletonList("order-id");
        break;
      case "exclude-only":
        expectedKeys = asList("order-id", "user-1", "user-22", "other");
        break;
      default:
        expectedKeys = asList("order-id", "user-1");
        break;
    }
    Map<String, String> expected = new HashMap<>();
    expectedKeys.forEach(key -> expected.put(key, MAP_MESSAGE_ENTRIES.get(key)));
    return expected;
  }
}
