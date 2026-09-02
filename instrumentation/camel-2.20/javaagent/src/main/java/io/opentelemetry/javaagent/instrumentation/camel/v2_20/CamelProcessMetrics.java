/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingMetricsState.hasConsumedMessages;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingMetricsState.hasProcessDuration;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveTelemetry;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import javax.annotation.Nullable;
import org.apache.camel.Exchange;
import org.apache.camel.Route;

class CamelProcessMetrics {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.camel-2.20";
  private static final String STATE_PROPERTY = CamelProcessMetrics.class.getName() + ".state";
  private static final ContextKey<Boolean> RECORD_CONSUMED_MESSAGES =
      ContextKey.named("camel-record-consumed-messages");
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

  static OperationMetrics consumedMessages() {
    return meter ->
        new ConsumedMessagesListener(MessagingConsumerMetrics.getConsumedMessages().create(meter));
  }

  static boolean wasConsumedMessageCounted(CamelRequest request) {
    Object message = request.getExchange().getIn();
    return JmsReceiveTelemetry.wasRecorded(message)
        || KafkaClientsConsumerProcessTracing.wasConsumedMessageCounted(message);
  }

  static void start(Route route, Context parentContext, CamelRequest request) {
    boolean recordConsumedMessages =
        !hasConsumedMessages(parentContext) && !wasConsumedMessageCounted(request);
    boolean recordProcessDuration = !hasProcessDuration(parentContext);
    if (!recordConsumedMessages && !recordProcessDuration) {
      return;
    }

    AttributesBuilder attributes = Attributes.builder();
    attributesExtractor.onStart(attributes, parentContext, request);
    Attributes startAttributes = attributes.build();
    long startNanos = System.nanoTime();
    Context metricsContext = parentContext;
    if (recordConsumedMessages) {
      metricsContext = consumedMessages.onStart(metricsContext, startAttributes, startNanos);
    }
    if (recordProcessDuration) {
      metricsContext = processDuration.onStart(metricsContext, startAttributes, startNanos);
    }
    request
        .getExchange()
        .setProperty(
            STATE_PROPERTY,
            new State(
                request.getExchange().getProperty(STATE_PROPERTY, State.class),
                route,
                metricsContext,
                request,
                recordConsumedMessages,
                recordProcessDuration));
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
    if (state.recordConsumedMessages) {
      consumedMessages.onEnd(state.context, endAttributes, endNanos);
    }
    if (state.recordProcessDuration) {
      processDuration.onEnd(state.context, endAttributes, endNanos);
    }
  }

  private static final class ConsumedMessagesListener implements OperationListener {

    private final OperationListener delegate;

    private ConsumedMessagesListener(OperationListener delegate) {
      this.delegate = delegate;
    }

    @Override
    public Context onStart(Context context, Attributes startAttributes, long startNanos) {
      if (hasConsumedMessages(context)) {
        return context;
      }
      return delegate
          .onStart(context, startAttributes, startNanos)
          .with(RECORD_CONSUMED_MESSAGES, true);
    }

    @Override
    public void onEnd(Context context, Attributes endAttributes, long endNanos) {
      if (Boolean.TRUE.equals(context.get(RECORD_CONSUMED_MESSAGES))) {
        delegate.onEnd(context, endAttributes, endNanos);
      }
    }
  }

  private static final class State {
    @Nullable private final State parent;
    private final Route route;
    private final Context context;
    private final CamelRequest request;
    private final boolean recordConsumedMessages;
    private final boolean recordProcessDuration;

    private State(
        @Nullable State parent,
        Route route,
        Context context,
        CamelRequest request,
        boolean recordConsumedMessages,
        boolean recordProcessDuration) {
      this.parent = parent;
      this.route = route;
      this.context = context;
      this.request = request;
      this.recordConsumedMessages = recordConsumedMessages;
      this.recordProcessDuration = recordProcessDuration;
    }
  }

  private CamelProcessMetrics() {}
}
