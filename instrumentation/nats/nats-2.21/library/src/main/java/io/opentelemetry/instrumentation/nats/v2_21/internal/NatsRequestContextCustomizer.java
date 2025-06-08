/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;

/**
 * This * class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.", or "This class is internal and experimental. Its APIs are unstable and can change at
 * any time. Its APIs (or a version of them) may be promoted to the public stable API in the future,
 * but no guarantees are made.
 */
public class NatsRequestContextCustomizer implements ContextCustomizer<NatsRequest> {
  private final TextMapPropagator propagator;

  public NatsRequestContextCustomizer(TextMapPropagator propagator) {
    this.propagator = propagator;
  }

  @Override
  /* In case of a `connection.request`, the replyTo will be set to an internal buffer prefixed
   * _INBOX.*. In this case we can consider a request-response pattern and a synchronous/short timed
   * window. This Customize will set the span as CHILD_OF and inside the same trace
   */
  public Context onStart(
      Context parentContext, NatsRequest natsRequest, Attributes startAttributes) {
    if (!Span.fromContext(parentContext).getSpanContext().isValid()
        && natsRequest.getReplyTo() != null
        && natsRequest.getReplyTo().startsWith("_INBOX.")) {
      return propagator.extract(parentContext, natsRequest, NatsRequestTextMapGetter.INSTANCE);
    }

    return parentContext;
  }
}
