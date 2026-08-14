/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v1_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.OtelAttributes.OTEL_EVENT_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Log4jMdcSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = Logger.getLogger("selector");

  private static final Map<String, String> MDC_ENTRIES = mdcEntries();

  @Test
  void capturesConfiguredMdcAttributes() {
    MDC_ENTRIES.forEach(MDC::put);
    MDC.put("otel.event.name", "MyEventName");
    try {
      logger.info("selector test");
    } finally {
      MDC_ENTRIES.keySet().forEach(MDC::remove);
      MDC.remove("otel.event.name");
    }

    testing.waitAndAssertLogRecords(
        logRecord -> {
          logRecord.hasEventName("MyEventName");
          Attributes attributes = logRecord.actual().getAttributes();
          assertThat(attributes.get(OTEL_EVENT_NAME)).isNull();
          assertThat(capturedMdcAttributes(attributes))
              .containsExactlyInAnyOrderEntriesOf(expectedMdcAttributes());
        });
  }

  private static Map<String, String> mdcEntries() {
    Map<String, String> entries = new HashMap<>();
    entries.put("exact", "exact-value");
    entries.put("prefix.value", "prefix-value");
    entries.put("prefix.secret", "secret-value");
    entries.put("single1", "single-value");
    entries.put("single22", "double-value");
    entries.put("legacy", "legacy-value");
    entries.put("new", "new-value");
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
        // the deprecated setting matches keys literally, so "*" does not capture every key
        expectedKeys = singletonList("legacy");
        break;
      case "precedence":
        expectedKeys = singletonList("new");
        break;
      case "exclude-only":
        expectedKeys = asList("exact", "prefix.value", "single1", "single22", "legacy", "new");
        break;
      default:
        expectedKeys = asList("exact", "prefix.value", "single1");
        break;
    }
    Map<String, String> expected = new HashMap<>();
    expectedKeys.forEach(key -> expected.put(key, MDC_ENTRIES.get(key)));
    return expected;
  }
}
