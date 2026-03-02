/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.logstash.logback.marker.Markers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogstashMarkerTest {
  private static final Logger logger = LoggerFactory.getLogger("TestLogger");

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeAll
  static void setupAll() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @Test
  void logstash() {
    Map<String, Object> entries = new HashMap<>();
    entries.put("field2", 2);
    entries.put("field3", "value3");

    logger
        .atInfo()
        .setMessage("log message 1")
        .addMarker(Markers.append("field1", "value1"))
        .addMarker(Markers.append("event.name", "MyEventName"))
        .addMarker(Markers.appendEntries(entries))
        .log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("log message 1")
                .hasTotalAttributeCount(3) // 3 markers (event.name handled separately)
                .hasEventName("MyEventName")
                .hasAttributesSatisfying(
                    equalTo(stringKey("field1"), "value1"),
                    equalTo(longKey("field2"), 2L),
                    equalTo(stringKey("field3"), "value3")));
  }

  @Test
  void logstashVariousValues() {
    Map<String, Object> entries = new HashMap<>();
    entries.put("map1", 1);
    entries.put("map2", 2.0);
    entries.put("map3", "text-5");
    entries.put("map4", null);

    logger
        .atInfo()
        .setMessage("log message 1")
        .addMarker(Markers.append("field1", 1))
        .addMarker(Markers.append("field2", 2.0))
        .addMarker(Markers.append("field3", "text-1"))
        .addMarker(Markers.append("field4", true))
        .addMarker(Markers.append("field5", new Integer[] {1, null, 2, 3}))
        .addMarker(Markers.append("field6", new double[] {1.0, 2.0, 3.0}))
        .addMarker(Markers.append("field7", new String[] {"text-2", "text-3", "text-4", null}))
        .addMarker(Markers.append("field8", new Boolean[] {true, false, true}))
        .addMarker(Markers.appendArray("field9", 1, 2.0, true, "text"))
        .addMarker(Markers.appendRaw("field10", "raw value"))
        .addMarker(Markers.append("field11", Arrays.asList(1, 2, 3)))
        .addMarker(Markers.appendEntries(entries))
        .log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("log message 1")
                // 14 fields (including map keys)
                .hasTotalAttributeCount(14)
                .hasAttributesSatisfying(
                    equalTo(longKey("field1"), 1L),
                    equalTo(doubleKey("field2"), 2.0),
                    equalTo(stringKey("field3"), "text-1"),
                    equalTo(booleanKey("field4"), true),
                    equalTo(longArrayKey("field5"), Arrays.asList(1L, 2L, 3L)),
                    equalTo(doubleArrayKey("field6"), Arrays.asList(1.0, 2.0, 3.0)),
                    equalTo(stringArrayKey("field7"), Arrays.asList("text-2", "text-3", "text-4")),
                    equalTo(booleanArrayKey("field8"), Arrays.asList(true, false, true)),
                    equalTo(stringArrayKey("field9"), Arrays.asList("1", "2.0", "true", "text")),
                    equalTo(stringKey("field10"), "raw value"),
                    equalTo(stringArrayKey("field11"), Arrays.asList("1", "2", "3")),
                    equalTo(longKey("map1"), 1L),
                    equalTo(doubleKey("map2"), 2.0),
                    equalTo(stringKey("map3"), "text-5")));
  }

  @Test
  void logstashEmptyAndNullValues() {
    Map<String, Object> noEntries = new HashMap<>();

    logger
        .atInfo()
        .setMessage("log message 1")
        .addMarker(Markers.appendEntries(noEntries))
        .addMarker(Markers.append("field2", null))
        .addMarker(Markers.append("field3", new int[0]))
        .addMarker(Markers.append("field4", new String[0]))
        .addMarker(Markers.appendArray("field5"))
        .addMarker(Markers.appendArray("field6", (Object) null))
        .addMarker(Markers.appendArray("field7", null, null, null))
        .log();

    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasBody("log message 1").hasTotalAttributeCount(0));
  }
}
