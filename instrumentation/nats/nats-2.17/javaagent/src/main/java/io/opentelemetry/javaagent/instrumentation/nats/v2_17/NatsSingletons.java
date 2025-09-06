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

public final class NatsSingletons {

  private static final List<String> capturedHeaders =
      ExperimentalConfig.get().getMessagingHeaders();

  public static final Instrumenter<NatsRequest, NatsRequest> PRODUCER_INSTRUMENTER =
      createProducerInstrumenter(GlobalOpenTelemetry.get(), capturedHeaders);

  public static final Instrumenter<NatsRequest, Void> CONSUMER_PROCESS_INSTRUMENTER =
      createConsumerProcessInstrumenter(GlobalOpenTelemetry.get(), capturedHeaders);

  private NatsSingletons() {}
}
