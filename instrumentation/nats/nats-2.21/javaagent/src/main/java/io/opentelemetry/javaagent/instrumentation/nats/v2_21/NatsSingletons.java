/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_21;

import static io.opentelemetry.instrumentation.nats.v2_21.internal.NatsInstrumenterFactory.createClientInstrumenter;
import static io.opentelemetry.instrumentation.nats.v2_21.internal.NatsInstrumenterFactory.createConsumerProcessInstrumenter;
import static io.opentelemetry.instrumentation.nats.v2_21.internal.NatsInstrumenterFactory.createConsumerReceiveInstrumenter;
import static io.opentelemetry.instrumentation.nats.v2_21.internal.NatsInstrumenterFactory.createProducerInstrumenter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;

public final class NatsSingletons {

  public static final Instrumenter<NatsRequest, Void> PRODUCER_INSTRUMENTER =
      createProducerInstrumenter(GlobalOpenTelemetry.get());

  public static final Instrumenter<NatsRequest, Void> CONSUMER_RECEIVE_INSTRUMENTER =
      createConsumerReceiveInstrumenter(GlobalOpenTelemetry.get());

  public static final Instrumenter<NatsRequest, Void> CONSUMER_PROCESS_INSTRUMENTER =
      createConsumerProcessInstrumenter(GlobalOpenTelemetry.get());

  public static final Instrumenter<NatsRequest, NatsRequest> CLIENT_INSTRUMENTER =
      createClientInstrumenter(GlobalOpenTelemetry.get());

  private NatsSingletons() {}
}
