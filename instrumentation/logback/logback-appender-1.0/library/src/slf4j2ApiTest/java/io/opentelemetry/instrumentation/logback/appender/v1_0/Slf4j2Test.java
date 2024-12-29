/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.logstash.logback.marker.Markers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

public class Slf4j2Test {
  private static final Logger logger = LoggerFactory.getLogger("TestLogger");

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private static Resource resource;
  private static InstrumentationScopeInfo instrumentationScopeInfo;

  @BeforeAll
  static void setupAll() {
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");

    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @Test
  void keyValue() {
    logger
        .atInfo()
        .setMessage("log message 1")
        .addKeyValue("string key", "string value")
        .addKeyValue("boolean key", true)
        .addKeyValue("byte key", (byte) 1)
        .addKeyValue("short key", (short) 2)
        .addKeyValue("int key", 3)
        .addKeyValue("long key", 4L)
        .addKeyValue("float key", 5.0f)
        .addKeyValue("double key", 6.0)
        .log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody("log message 1")
                .hasTotalAttributeCount(12) // 4 code attributes + 8 key value pairs
                .hasAttributesSatisfying(
                    equalTo(AttributeKey.stringKey("string key"), "string value"),
                    equalTo(AttributeKey.booleanKey("boolean key"), true),
                    equalTo(AttributeKey.longKey("byte key"), 1),
                    equalTo(AttributeKey.longKey("short key"), 2),
                    equalTo(AttributeKey.longKey("int key"), 3),
                    equalTo(AttributeKey.longKey("long key"), 4),
                    equalTo(AttributeKey.doubleKey("float key"), 5.0),
                    equalTo(AttributeKey.doubleKey("double key"), 6.0)));
  }

  @Test
  void multipleMarkers() {
    String markerName1 = "aMarker1";
    String markerName2 = "aMarker2";
    logger
        .atInfo()
        .setMessage("log message 1")
        .addMarker(MarkerFactory.getMarker(markerName1))
        .addMarker(MarkerFactory.getMarker(markerName2))
        .log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody("log message 1")
                .hasTotalAttributeCount(5) // 4 code attributes + 1 marker
                .hasAttributesSatisfying(
                    equalTo(
                        AttributeKey.stringArrayKey("logback.marker"),
                        Arrays.asList(markerName1, markerName2))));
  }

  @Test
  void arguments() {
    logger
        .atInfo()
        .setMessage("log message {} and {}, bool {}, long {}")
        .addArgument("'world'")
        .addArgument(Math.PI)
        .addArgument(true)
        .addArgument(Long.MAX_VALUE)
        .log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody(
                    "log message 'world' and 3.141592653589793, bool true, long 9223372036854775807")
                .hasTotalAttributeCount(6)
                .hasAttributesSatisfying(
                    equalTo(
                        AttributeKey.stringArrayKey("log.body.parameters"),
                        Arrays.asList(
                            "'world'",
                            String.valueOf(Math.PI),
                            String.valueOf(true),
                            String.valueOf(Long.MAX_VALUE))),
                    equalTo(
                        AttributeKey.stringKey("log.body.template"),
                        "log message {} and {}, bool {}, long {}")));
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
        .addMarker(Markers.appendEntries(entries))
        .log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody("log message 1")
                .hasTotalAttributeCount(7) // 4 code attributes + 3 markers
                .hasAttributesSatisfying(
                    equalTo(AttributeKey.stringKey("field1"), "value1"),
                    equalTo(AttributeKey.longKey("field2"), 2L),
                    equalTo(AttributeKey.stringKey("field3"), "value3")));
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
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody("log message 1")
                .hasTotalAttributeCount(18) // 4 code attributes + 14 fields (including map keys)
                .hasAttributesSatisfying(
                    equalTo(AttributeKey.longKey("field1"), 1L),
                    equalTo(AttributeKey.doubleKey("field2"), 2.0),
                    equalTo(AttributeKey.stringKey("field3"), "text-1"),
                    equalTo(AttributeKey.booleanKey("field4"), true),
                    equalTo(AttributeKey.longArrayKey("field5"), Arrays.asList(1L, 2L, 3L)),
                    equalTo(AttributeKey.doubleArrayKey("field6"), Arrays.asList(1.0, 2.0, 3.0)),
                    equalTo(
                        AttributeKey.stringArrayKey("field7"),
                        Arrays.asList("text-2", "text-3", "text-4")),
                    equalTo(
                        AttributeKey.booleanArrayKey("field8"), Arrays.asList(true, false, true)),
                    equalTo(
                        AttributeKey.stringArrayKey("field9"),
                        Arrays.asList("1", "2.0", "true", "text")),
                    equalTo(AttributeKey.stringKey("field10"), "raw value"),
                    equalTo(AttributeKey.stringArrayKey("field11"), Arrays.asList("1", "2", "3")),
                    equalTo(AttributeKey.longKey("map1"), 1L),
                    equalTo(AttributeKey.doubleKey("map2"), 2.0),
                    equalTo(AttributeKey.stringKey("map3"), "text-5")));
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
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody("log message 1")
                .hasTotalAttributeCount(4) // 4 code attributes
        );
  }
}
