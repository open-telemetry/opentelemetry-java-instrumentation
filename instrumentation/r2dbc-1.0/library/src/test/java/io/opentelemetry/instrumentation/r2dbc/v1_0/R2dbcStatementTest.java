/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.R2dbcNetAttributesGetter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class R2dbcStatementTest extends AbstractR2dbcStatementTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final ContextPropagationOperator tracingOperator = ContextPropagationOperator.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @BeforeAll
  void setup() {
    tracingOperator.registerOnEachOperator();
  }

  @AfterAll
  void stop() {
    tracingOperator.resetOnEachOperator();
  }

  @Override
  protected ConnectionFactory createProxyConnectionFactory(
      ConnectionFactoryOptions connectionFactoryOptions) {
    // Create peer service mapping for testing
    java.util.Map<String, String> peerServiceMapping = new java.util.HashMap<>();
    peerServiceMapping.put("127.0.0.1", "test-peer-service");
    peerServiceMapping.put("localhost", "test-peer-service");
    peerServiceMapping.put("192.0.2.1", "test-peer-service");

    return R2dbcTelemetry.builder(testing.getOpenTelemetry())
        .addAttributesExtractor(
            PeerServiceAttributesExtractor.create(
                R2dbcNetAttributesGetter.INSTANCE, PeerServiceResolver.create(peerServiceMapping)))
        .build()
        .wrapConnectionFactory(
            super.createProxyConnectionFactory(connectionFactoryOptions), connectionFactoryOptions);
  }
}
