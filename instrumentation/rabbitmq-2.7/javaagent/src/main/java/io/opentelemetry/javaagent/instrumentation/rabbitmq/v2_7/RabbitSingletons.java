/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingReceiveExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSettleExceptionEventExtractor;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RabbitSingletons {

  // Shared with framework instrumentations through the application Consumer type.
  public static final VirtualField<Consumer, Boolean> PROCESSING_OWNED_OUTSIDE_RABBIT_CLIENT =
      VirtualField.find(Consumer.class, Boolean.class);

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rabbitmq-2.7";

  // messaging.operation.name values, named after the RabbitMQ client API operations
  private static final String PUBLISH_OPERATION_NAME = "publish";
  private static final String RECEIVE_OPERATION_NAME = "receive";
  private static final String PROCESS_OPERATION_NAME = "process";
  private static final String ACK_OPERATION_NAME = "ack";
  private static final String NACK_OPERATION_NAME = "nack";
  private static final String REJECT_OPERATION_NAME = "reject";

  private static final Instrumenter<ChannelAndMethod, Void> channelInstrumenter =
      createChannelInstrumenter();
  private static final Instrumenter<ChannelAndMethod, Void> channelPublishInstrumenter =
      createChannelPublishInstrumenter();
  private static final Map<String, Instrumenter<ChannelAndMethod, Void>>
      channelSettleInstrumenters = createChannelSettleInstrumenters();
  private static final Instrumenter<ReceiveRequest, GetResponse> receiveInstrumenter =
      createReceiveInstrumenter();
  private static final Instrumenter<DeliveryRequest, Void> deliverInstrumenter =
      createDeliverInstrumenter();
  static final ContextKey<RabbitChannelAndMethodHolder> CHANNEL_AND_METHOD_CONTEXT_KEY =
      ContextKey.named("opentelemetry-rabbitmq-channel-and-method-context-key");

  /**
   * The virtual host of a connection. {@link Connection} has never exposed it, so it is read off
   * {@code AMQConnection} and remembered here.
   */
  public static final VirtualField<Connection, String> VIRTUAL_HOST =
      VirtualField.find(Connection.class, String.class);

  public static Instrumenter<ChannelAndMethod, Void> channelInstrumenter(
      ChannelAndMethod channelAndMethod) {
    if (channelAndMethod.isPublish()) {
      return channelPublishInstrumenter;
    }
    Instrumenter<ChannelAndMethod, Void> settleInstrumenter =
        channelSettleInstrumenters.get(channelAndMethod.getMethod());
    return settleInstrumenter != null ? settleInstrumenter : channelInstrumenter;
  }

  public static Instrumenter<ReceiveRequest, GetResponse> receiveInstrumenter() {
    return receiveInstrumenter;
  }

  static Instrumenter<DeliveryRequest, Void> deliverInstrumenter() {
    return deliverInstrumenter;
  }

  private static InstrumenterBuilder<ChannelAndMethod, Void> channelInstrumenterBuilder(
      SpanNameExtractor<ChannelAndMethod> spanNameExtractor, boolean messagingOperation) {
    RabbitChannelNetAttributesGetter netAttributesGetter = new RabbitChannelNetAttributesGetter();
    InstrumenterBuilder<ChannelAndMethod, Void> builder =
        Instrumenter.<ChannelAndMethod, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter))
            .addContextCustomizer(
                (context, request, startAttributes) ->
                    context.with(
                        CHANNEL_AND_METHOD_CONTEXT_KEY, new RabbitChannelAndMethodHolder()));
    if (messagingOperation) {
      builder.addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter));
    }
    if (RabbitInstrumenterHelper.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.addAttributesExtractor(
          new RabbitConnectionAttributesExtractor<>(
              channelAndMethod -> channelAndMethod.getChannel().getConnection()));
    }
    return builder;
  }

  private static Instrumenter<ChannelAndMethod, Void> createChannelInstrumenter() {
    return channelInstrumenterBuilder(ChannelAndMethod::getMethod, false)
        .addAttributesExtractor(AttributesExtractor.constant(MESSAGING_SYSTEM, "rabbitmq"))
        .buildInstrumenter(channelAndMethod -> CLIENT);
  }

  private static Instrumenter<ChannelAndMethod, Void> createChannelPublishInstrumenter() {
    RabbitChannelAttributesGetter getter = new RabbitChannelAttributesGetter();
    InstrumenterBuilder<ChannelAndMethod, Void> builder =
        channelInstrumenterBuilder(
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.SEND, PUBLISH_OPERATION_NAME),
                true)
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, MessagingOperationType.SEND, PUBLISH_OPERATION_NAME))
            .addAttributesExtractor(new RabbitChannelExtraAttributesExtractor())
            .addOperationMetrics(MessagingProducerMetrics.get());
    setMessagingSendExceptionEventExtractor(builder);
    return builder.buildInstrumenter(channelAndMethod -> PRODUCER);
  }

  private static Map<String, Instrumenter<ChannelAndMethod, Void>>
      createChannelSettleInstrumenters() {
    Map<String, Instrumenter<ChannelAndMethod, Void>> instrumenters = new HashMap<>();
    instrumenters.put(
        ChannelAndMethod.ACK_METHOD, createChannelSettleInstrumenter(ACK_OPERATION_NAME));
    instrumenters.put(
        ChannelAndMethod.NACK_METHOD, createChannelSettleInstrumenter(NACK_OPERATION_NAME));
    instrumenters.put(
        ChannelAndMethod.REJECT_METHOD, createChannelSettleInstrumenter(REJECT_OPERATION_NAME));
    return instrumenters;
  }

  private static Instrumenter<ChannelAndMethod, Void> createChannelSettleInstrumenter(
      String operationName) {
    RabbitChannelAttributesGetter getter = new RabbitChannelAttributesGetter();
    InstrumenterBuilder<ChannelAndMethod, Void> builder =
        channelInstrumenterBuilder(
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.SETTLE, operationName),
                true)
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, MessagingOperationType.SETTLE, operationName))
            .addAttributesExtractor(new RabbitChannelSettleAttributesExtractor())
            .addOperationMetrics(MessagingConsumerMetrics.get());
    setMessagingSettleExceptionEventExtractor(builder);
    return builder.buildInstrumenter(
        MessagingSpanKindExtractor.create(MessagingOperationType.SETTLE));
  }

  private static Instrumenter<ReceiveRequest, GetResponse> createReceiveInstrumenter() {
    RabbitReceiveAttributesGetter getter = new RabbitReceiveAttributesGetter();
    List<AttributesExtractor<ReceiveRequest, GetResponse>> extractors = new ArrayList<>();
    extractors.add(
        buildMessagingAttributesExtractor(
            getter, MessagingOperationType.RECEIVE, RECEIVE_OPERATION_NAME));
    extractors.add(new RabbitReceiveExtraAttributesExtractor());
    RabbitReceiveNetAttributesGetter netAttributesGetter = new RabbitReceiveNetAttributesGetter();
    extractors.add(NetworkAttributesExtractor.create(netAttributesGetter));
    extractors.add(ServerAttributesExtractor.create(netAttributesGetter));
    if (RabbitInstrumenterHelper.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitReceiveExperimentalAttributesExtractor());
      extractors.add(new RabbitConnectionAttributesExtractor<>(ReceiveRequest::getConnection));
    }

    SpanNameExtractor<ReceiveRequest> spanNameExtractor =
        MessagingSpanNameExtractor.create(
            getter, MessagingOperationType.RECEIVE, RECEIVE_OPERATION_NAME);
    InstrumenterBuilder<ReceiveRequest, GetResponse> builder =
        Instrumenter.<ReceiveRequest, GetResponse>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractors(extractors)
            .addOperationMetrics(MessagingConsumerMetrics.get())
            .addSpanLinksExtractor(
                new PropagatorBasedSpanLinksExtractor<>(
                    GlobalOpenTelemetry.getPropagators().getTextMapPropagator(),
                    new ReceiveRequestTextMapGetter()));
    setMessagingReceiveExceptionEventExtractor(builder);
    return builder.buildInstrumenter(
        MessagingSpanKindExtractor.create(MessagingOperationType.RECEIVE));
  }

  private static Instrumenter<DeliveryRequest, Void> createDeliverInstrumenter() {
    RabbitDeliveryAttributesGetter getter = new RabbitDeliveryAttributesGetter();
    List<AttributesExtractor<DeliveryRequest, Void>> extractors = new ArrayList<>();
    extractors.add(
        buildMessagingAttributesExtractor(
            getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME));
    RabbitDeliveryNetAttributesGetter netAttributesGetter = new RabbitDeliveryNetAttributesGetter();
    extractors.add(NetworkAttributesExtractor.create(netAttributesGetter));
    extractors.add(ServerAttributesExtractor.create(netAttributesGetter));
    extractors.add(new RabbitDeliveryExtraAttributesExtractor());
    if (RabbitInstrumenterHelper.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitDeliveryExperimentalAttributesExtractor());
      extractors.add(new RabbitConnectionAttributesExtractor<>(DeliveryRequest::getConnection));
    }

    SpanNameExtractor<DeliveryRequest> spanNameExtractor =
        MessagingSpanNameExtractor.create(
            getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME);
    InstrumenterBuilder<DeliveryRequest, Void> builder =
        Instrumenter.<DeliveryRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractors(extractors)
            .addOperationMetrics(MessagingProcessMetrics.get())
            .addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder,
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator(),
        new DeliveryRequestGetter());
  }

  private static <T, V> AttributesExtractor<T, V> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, V> getter,
      MessagingOperationType operationType,
      String operationName) {
    return MessagingAttributesExtractor.builder(getter, operationType, operationName)
        .setHeaders(ExperimentalConfig.get().getMessagingHeaders())
        .build();
  }

  private RabbitSingletons() {}
}
