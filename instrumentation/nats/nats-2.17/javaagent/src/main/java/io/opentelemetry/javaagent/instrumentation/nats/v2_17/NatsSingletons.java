/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory.createConsumerProcessInstrumenter;
import static io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory.createProducerInstrumenter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.List;

class NatsSingletons {

  private static final List<String> capturedHeaders =
      ExperimentalConfig.get().getMessagingHeaders();

  private static final Instrumenter<NatsRequest, NatsRequest> producerInstrumenter =
      createProducerInstrumenter(GlobalOpenTelemetry.get(), capturedHeaders);

  private static final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter =
      createConsumerProcessInstrumenter(GlobalOpenTelemetry.get(), capturedHeaders);

  static Instrumenter<NatsRequest, NatsRequest> producerInstrumenter() {
    return producerInstrumenter;
  }

  static Instrumenter<NatsRequest, Void> consumerProcessInstrumenter() {
    return consumerProcessInstrumenter;
  }

  private NatsSingletons() {}
}
