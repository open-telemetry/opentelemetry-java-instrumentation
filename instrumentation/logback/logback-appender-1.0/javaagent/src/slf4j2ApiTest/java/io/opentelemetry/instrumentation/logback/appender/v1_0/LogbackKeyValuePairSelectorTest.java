/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

class LogbackKeyValuePairSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = (Logger) LoggerFactory.getLogger("key-value-pair-selector");

  private static final Map<String, String> KEY_VALUE_PAIRS = keyValuePairs();

  @Test
  void capturesConfiguredKeyValuePairAttributes() {
    logger.setLevel(Level.INFO);
    LoggingEventBuilder eventBuilder = logger.atInfo();
    for (Map.Entry<String, String> entry : KEY_VALUE_PAIRS.entrySet()) {
      eventBuilder = eventBuilder.addKeyValue(entry.getKey(), entry.getValue());
    }
    eventBuilder.log("selector test");

    testing.waitAndAssertLogRecords(
        logRecord -> {
          Attributes attributes = logRecord.actual().getAttributes();
          assertThat(capturedKeyValuePairAttributes(attributes))
              .containsExactlyInAnyOrderEntriesOf(expectedKeyValuePairAttributes());
        });
  }

  private static Map<String, String> keyValuePairs() {
    Map<String, String> entries = new HashMap<>();
    entries.put("key1", "key1-value");
    entries.put("key2", "key2-value");
    return entries;
  }

  private static Map<String, String> capturedKeyValuePairAttributes(Attributes attributes) {
    Map<String, String> captured = new HashMap<>();
    KEY_VALUE_PAIRS.forEach(
        (key, value) -> {
          String attributeValue = attributes.get(stringKey(key));
          if (attributeValue != null) {
            captured.put(key, attributeValue);
          }
        });
    return captured;
  }

  private static Map<String, String> expectedKeyValuePairAttributes() {
    if ("legacy".equals(System.getProperty("testKeyValuePairConfiguration"))) {
      return new HashMap<>(KEY_VALUE_PAIRS);
    }
    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "key1-value");
    return expected;
  }
}
