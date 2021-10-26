/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

/** Entrypoint for instrumenting Spring Integration {@link MessageChannel}s. */
public final class SpringIntegrationTracing {

  /**
   * Returns a new {@link SpringIntegrationTracing} configured with the given {@link OpenTelemetry}.
   */
  public static SpringIntegrationTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SpringIntegrationTracingBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static SpringIntegrationTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringIntegrationTracingBuilder(openTelemetry);
  }

  private final ContextPropagators propagators;
  private final Instrumenter<MessageWithChannel, Void> instrumenter;

  SpringIntegrationTracing(
      ContextPropagators propagators, Instrumenter<MessageWithChannel, Void> instrumenter) {
    this.propagators = propagators;
    this.instrumenter = instrumenter;
  }

  /**
   * Returns a new {@link ChannelInterceptor} that propagates context through {@link Message}s and
   * when no other messaging instrumentation is detected, traces {@link
   * MessageChannel#send(Message)} calls.
   *
   * @see org.springframework.integration.channel.ChannelInterceptorAware
   * @see org.springframework.messaging.support.InterceptableChannel
   * @see org.springframework.integration.config.GlobalChannelInterceptor
   */
  public ChannelInterceptor newChannelInterceptor() {
    return new TracingChannelInterceptor(propagators, instrumenter);
  }
}
