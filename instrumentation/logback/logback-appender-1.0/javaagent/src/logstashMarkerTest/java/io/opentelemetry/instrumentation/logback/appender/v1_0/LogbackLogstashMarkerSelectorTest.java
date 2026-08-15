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
import net.logstash.logback.marker.Markers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogbackLogstashMarkerSelectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("logstash-marker-selector");

  private static final Map<String, String> MARKER_ATTRIBUTES = markerAttributes();

  @Test
  void capturesConfiguredLogstashMarkerAttributes() {
    logger
        .atInfo()
        .setMessage("selector test")
        .addMarker(Markers.append("key1", "key1-value"))
        .addMarker(Markers.append("key2", "key2-value"))
        .addMarker(Markers.append("other", "other-value"))
        .log();

    testing.waitAndAssertLogRecords(
        logRecord -> {
          Attributes attributes = logRecord.actual().getAttributes();
          assertThat(capturedMarkerAttributes(attributes))
              .containsExactlyInAnyOrderEntriesOf(expectedMarkerAttributes());
        });
  }

  private static Map<String, String> markerAttributes() {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("key1", "key1-value");
    attributes.put("key2", "key2-value");
    attributes.put("other", "other-value");
    return attributes;
  }

  private static Map<String, String> capturedMarkerAttributes(Attributes attributes) {
    Map<String, String> captured = new HashMap<>();
    MARKER_ATTRIBUTES.forEach(
        (key, value) -> {
          String attributeValue = attributes.get(stringKey(key));
          if (attributeValue != null) {
            captured.put(key, attributeValue);
          }
        });
    return captured;
  }

  private static Map<String, String> expectedMarkerAttributes() {
    List<String> expectedKeys;
    switch (System.getProperty("testLogstashMarkerConfiguration", "new")) {
      case "legacy":
        // the deprecated setting captures every Logstash marker attribute
        return new HashMap<>(MARKER_ATTRIBUTES);
      case "precedence":
        expectedKeys = singletonList("key1");
        break;
      case "exclude-only":
        expectedKeys = asList("key1", "key2");
        break;
      default:
        // the test task configures logstash-marker-attributes.included=key?
        expectedKeys = asList("key1", "key2");
        break;
    }
    Map<String, String> expected = new HashMap<>();
    expectedKeys.forEach(key -> expected.put(key, MARKER_ATTRIBUTES.get(key)));
    return expected;
  }
}
