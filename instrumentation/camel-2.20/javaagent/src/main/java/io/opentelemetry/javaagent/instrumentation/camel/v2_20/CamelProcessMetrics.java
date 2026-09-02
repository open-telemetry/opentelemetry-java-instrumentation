/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingMetricsState.markConsumedMessages;
import static io.opentelemetry.javaagent.bootstrap.MessagingMetricCarrier.hasConsumedMessages;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import javax.annotation.Nullable;
import org.apache.camel.Exchange;
import org.apache.camel.Route;

class CamelProcessMetrics {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.camel-2.20";
  private static final String STATE_PROPERTY = CamelProcessMetrics.class.getName() + ".state";
  private static final Meter meter = createMeter();
  private static final AttributesExtractor<CamelRequest, Void> attributesExtractor =
      MessagingAttributesExtractor.create(new CamelMessagingAttributesGetter(), PROCESS, "process");
  private static final OperationListener consumedMessages =
      MessagingConsumerMetrics.getConsumedMessages().create(meter);
  private static final OperationListener processDuration =
      MessagingProcessMetrics.get().create(meter);

  private static Meter createMeter() {
    MeterBuilder meterBuilder =
        GlobalOpenTelemetry.get().getMeterProvider().meterBuilder(INSTRUMENTATION_NAME);
    String version = EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return meterBuilder.build();
  }

  static Context withConsumedMessagesClaim(Context context, CamelRequest request) {
    Object message = request.getExchange().getIn();
    return hasConsumedMessages(message) ? markConsumedMessages(context) : context;
  }

  static void start(Route route, Context parentContext, CamelRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    attributesExtractor.onStart(attributes, parentContext, request);
    Attributes startAttributes = attributes.build();
    long startNanos = System.nanoTime();
    Context metricsContext = withConsumedMessagesClaim(parentContext, request);
    metricsContext = consumedMessages.onStart(metricsContext, startAttributes, startNanos);
    metricsContext = processDuration.onStart(metricsContext, startAttributes, startNanos);
    request
        .getExchange()
        .setProperty(
            STATE_PROPERTY,
            new State(
                request.getExchange().getProperty(STATE_PROPERTY, State.class),
                route,
                metricsContext,
                request));
  }

  static void end(Route route, Exchange exchange) {
    State state = exchange.getProperty(STATE_PROPERTY, State.class);
    if (state == null || state.route != route) {
      return;
    }
    exchange.setProperty(STATE_PROPERTY, state.parent);

    AttributesBuilder attributes = Attributes.builder();
    Exception error = exchange.getException();
    attributesExtractor.onEnd(attributes, state.context, state.request, null, error);
    Attributes endAttributes = attributes.build();
    long endNanos = System.nanoTime();
    processDuration.onEnd(state.context, endAttributes, endNanos);
    consumedMessages.onEnd(state.context, endAttributes, endNanos);
  }

  private static final class State {
    @Nullable private final State parent;
    private final Route route;
    private final Context context;
    private final CamelRequest request;

    private State(@Nullable State parent, Route route, Context context, CamelRequest request) {
      this.parent = parent;
      this.route = route;
      this.context = context;
      this.request = request;
    }
  }

  private CamelProcessMetrics() {}
}
