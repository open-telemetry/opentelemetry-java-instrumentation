/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory.createConsumerProcessInstrumenter;
import static io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory.createPublishInstrumenter;
import static io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory.createRequestInstrumenter;
import static io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory.createSettleInstrumenter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

class NatsSingletons {

  private static final IncludeExclude headers = ExperimentalConfig.get().getMessagingHeaders();

  private static final Instrumenter<NatsRequest, NatsRequest> publishInstrumenter =
      createPublishInstrumenter(GlobalOpenTelemetry.get(), headers);

  private static final Instrumenter<NatsRequest, NatsRequest> requestInstrumenter =
      createRequestInstrumenter(GlobalOpenTelemetry.get(), headers);

  private static final Instrumenter<NatsRequest, NatsRequest> settleInstrumenter =
      createSettleInstrumenter(GlobalOpenTelemetry.get(), headers);

  private static final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter =
      createConsumerProcessInstrumenter(GlobalOpenTelemetry.get(), headers);

  static Instrumenter<NatsRequest, NatsRequest> publishInstrumenter() {
    return publishInstrumenter;
  }

  static Instrumenter<NatsRequest, NatsRequest> requestInstrumenter() {
    return requestInstrumenter;
  }

  static Instrumenter<NatsRequest, NatsRequest> settleInstrumenter() {
    return settleInstrumenter;
  }

  static Instrumenter<NatsRequest, Void> consumerProcessInstrumenter() {
    return consumerProcessInstrumenter;
  }

  private NatsSingletons() {}
}
