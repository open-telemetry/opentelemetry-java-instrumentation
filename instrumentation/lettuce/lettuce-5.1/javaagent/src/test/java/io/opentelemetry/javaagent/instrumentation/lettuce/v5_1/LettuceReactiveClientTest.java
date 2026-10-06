/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;

import io.lettuce.core.RedisClient;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.lettuce.v5_1.AbstractLettuceClientTest;
import io.opentelemetry.instrumentation.lettuce.v5_1.AbstractLettuceReactiveClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LettuceReactiveClientTest extends AbstractLettuceReactiveClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  public InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected RedisClient createClient(String uri) {
    return RedisClient.create(uri);
  }

  // TODO: reactor library instrumentation doesn't seem to handle this case, figure out if it
  // should and if so move back to base class.
  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testAsyncSubscriberWithSpecificThreadPool() {
    testing()
        .runWithSpan(
            "test-parent",
            () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).subscribe());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("test-parent").hasTotalAttributeCount(0),
                    span ->
                        span.hasName(spanName("SET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "SET a ?"),
                                    equalTo(DB_OPERATION_NAME, "SET")))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents),
                    span ->
                        span.hasName(spanName("GET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "GET a"),
                                    equalTo(DB_OPERATION_NAME, "GET")))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }
}
