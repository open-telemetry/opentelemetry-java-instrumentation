/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFileAndLineAssertions;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;

class Slf4j2Test {
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
        .addKeyValue("otel.event.name", "MyEventName")
        .log();

    List<AttributeAssertion> assertions = codeAssertions("keyValue");
    assertions.add(equalTo(stringKey("string key"), "string value"));
    assertions.add(equalTo(booleanKey("boolean key"), true));
    assertions.add(equalTo(longKey("byte key"), 1));
    assertions.add(equalTo(longKey("short key"), 2));
    assertions.add(equalTo(longKey("int key"), 3));
    assertions.add(equalTo(longKey("long key"), 4));
    assertions.add(equalTo(doubleKey("float key"), 5.0));
    assertions.add(equalTo(doubleKey("double key"), 6.0));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody("log message 1")
                .hasEventName("MyEventName")
                .hasAttributesSatisfyingExactly(assertions));
  }

  @Test
  void otelEventNameKeyValue() {
    logger
        .atInfo()
        .setMessage("log message 1")
        .addKeyValue("otel.event.name", "MyEventName")
        .addKeyValue("key1", "val1")
        .log();

    List<AttributeAssertion> assertions = codeAssertions("otelEventNameKeyValue");
    assertions.add(equalTo(stringKey("key1"), "val1"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody("log message 1")
                .hasEventName("MyEventName")
                .hasAttributesSatisfyingExactly(assertions));
  }

  @Test
  void keyValuePairWinsOverMdc() {
    MDC.put("key1", "mdc-value");
    try {
      logger.atInfo().setMessage("test message").addKeyValue("key1", "kvp-value").log();
    } finally {
      MDC.clear();
    }

    List<AttributeAssertion> assertions = codeAssertions("keyValuePairWinsOverMdc");
    assertions.add(equalTo(stringKey("key1"), "kvp-value"));

    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasBody("test message").hasAttributesSatisfyingExactly(assertions));
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

    List<AttributeAssertion> assertions = codeAssertions("multipleMarkers");
    assertions.add(equalTo(stringArrayKey("logback.marker"), asList(markerName1, markerName2)));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody("log message 1")
                .hasAttributesSatisfyingExactly(assertions));
  }

  @Test
  void argumentsAndTemplate() {
    logger
        .atInfo()
        .setMessage("log message {} and {}, bool {}, long {}")
        .addArgument("'world'")
        .addArgument(Math.PI)
        .addArgument(true)
        .addArgument(Long.MAX_VALUE)
        .log();

    List<AttributeAssertion> assertions = codeAssertions("argumentsAndTemplate");
    assertions.add(
        equalTo(
            stringArrayKey("log.body.parameters"),
            asList(
                "'world'",
                String.valueOf(Math.PI),
                String.valueOf(true),
                String.valueOf(Long.MAX_VALUE))));
    assertions.add(
        equalTo(stringKey("log.body.template"), "log message {} and {}, bool {}, long {}"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(resource)
                .hasInstrumentationScope(instrumentationScopeInfo)
                .hasBody(
                    "log message 'world' and 3.141592653589793, bool true, long 9223372036854775807")
                .hasAttributesSatisfyingExactly(assertions));
  }

  private static List<AttributeAssertion> codeAssertions(String methodName) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.addAll(codeFileAndLineAssertions(Slf4j2Test.class.getSimpleName() + ".java"));
    assertions.addAll(codeFunctionAssertions(Slf4j2Test.class, methodName));
    return assertions;
  }
}
