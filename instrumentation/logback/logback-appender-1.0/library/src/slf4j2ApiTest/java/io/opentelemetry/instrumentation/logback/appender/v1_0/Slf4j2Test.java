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
}
