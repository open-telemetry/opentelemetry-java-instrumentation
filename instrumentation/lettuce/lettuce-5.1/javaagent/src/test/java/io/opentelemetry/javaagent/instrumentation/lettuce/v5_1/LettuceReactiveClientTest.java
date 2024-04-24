/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.lettuce.core.RedisClient;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.lettuce.v5_1.AbstractLettuceReactiveClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LettuceReactiveClientTest extends AbstractLettuceReactiveClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  public InstrumentationExtension getInstrumentationExtension() {
    return testing;
  }

  @Override
  protected RedisClient createClient(String uri) {
    return RedisClient.create(uri);
  }

  // TODO(anuraaga): reactor library instrumentation doesn't seem to handle this case, figure out if
  // it should and if so move back to base class.
  @Test
  void testAsyncSubscriberWithSpecificThreadPool() {
    getInstrumentationExtension()
        .runWithSpan(
            "test-parent",
            () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).subscribe());

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "SET a ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end")),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "GET a"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }
}
