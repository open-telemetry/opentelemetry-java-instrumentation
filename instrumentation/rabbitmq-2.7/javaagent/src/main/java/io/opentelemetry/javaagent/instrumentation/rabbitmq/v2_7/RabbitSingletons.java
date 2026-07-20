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
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.ArrayList;
import java.util.List;

public class RabbitSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rabbitmq-2.7";
  private static final Instrumenter<ChannelAndMethod, Void> channelInstrumenter =
      createChannelInstrumenter(false);
  private static final Instrumenter<ChannelAndMethod, Void> channelPublishInstrumenter =
      createChannelInstrumenter(true);
  private static final Instrumenter<ReceiveRequest, GetResponse> receiveInstrumenter =
      createReceiveInstrumenter();
  private static final Instrumenter<DeliveryRequest, Void> deliverInstrumenter =
      createDeliverInstrumenter();
  static final ContextKey<RabbitChannelAndMethodHolder> CHANNEL_AND_METHOD_CONTEXT_KEY =
      ContextKey.named("opentelemetry-rabbitmq-channel-and-method-context-key");

  public static Instrumenter<ChannelAndMethod, Void> channelInstrumenter(
      ChannelAndMethod channelAndMethod) {
    return channelAndMethod.getMethod().equals("Channel.basicPublish")
        ? channelPublishInstrumenter
        : channelInstrumenter;
  }

  public static Instrumenter<ReceiveRequest, GetResponse> receiveInstrumenter() {
    return receiveInstrumenter;
  }

  static Instrumenter<DeliveryRequest, Void> deliverInstrumenter() {
    return deliverInstrumenter;
  }

  private static Instrumenter<ChannelAndMethod, Void> createChannelInstrumenter(boolean publish) {
    RabbitChannelNetAttributesGetter netAttributesGetter = new RabbitChannelNetAttributesGetter();
    InstrumenterBuilder<ChannelAndMethod, Void> builder =
        Instrumenter.<ChannelAndMethod, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, ChannelAndMethod::getMethod)
            .addAttributesExtractor(
                publish
                    ? buildMessagingAttributesExtractor(
                        new RabbitChannelAttributesGetter(), MessagingOperationType.SEND)
                    : AttributesExtractor.constant(MESSAGING_SYSTEM, "rabbitmq"))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter))
            .addContextCustomizer(
                (context, request, startAttributes) ->
                    context.with(
                        CHANNEL_AND_METHOD_CONTEXT_KEY, new RabbitChannelAndMethodHolder()));
    if (publish && emitStableMessagingSemconv()) {
      builder.addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter));
    }
    if (publish) {
      setMessagingSendExceptionEventExtractor(builder);
    }
    return builder.buildInstrumenter(channelAndMethod -> publish ? PRODUCER : CLIENT);
  }

  private static Instrumenter<ReceiveRequest, GetResponse> createReceiveInstrumenter() {
    RabbitReceiveAttributesGetter getter = new RabbitReceiveAttributesGetter();
    List<AttributesExtractor<ReceiveRequest, GetResponse>> extractors = new ArrayList<>();
    extractors.add(buildMessagingAttributesExtractor(getter, MessagingOperationType.RECEIVE));
    extractors.add(new RabbitReceiveExtraAttributesExtractor());
    RabbitReceiveNetAttributesGetter netAttributesGetter = new RabbitReceiveNetAttributesGetter();
    extractors.add(NetworkAttributesExtractor.create(netAttributesGetter));
    if (emitStableMessagingSemconv()) {
      extractors.add(ServerAttributesExtractor.create(netAttributesGetter));
    }
    if (RabbitInstrumenterHelper.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitReceiveExperimentalAttributesExtractor());
    }

    SpanNameExtractor<ReceiveRequest> spanNameExtractor =
        emitStableMessagingSemconv()
            ? MessagingSpanNameExtractor.createForOperationType(
                getter, MessagingOperationType.RECEIVE)
            : ReceiveRequest::spanName;
    InstrumenterBuilder<ReceiveRequest, GetResponse> builder =
        Instrumenter.<ReceiveRequest, GetResponse>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractors(extractors)
            .setEnabled(ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
            .addSpanLinksExtractor(
                new PropagatorBasedSpanLinksExtractor<>(
                    GlobalOpenTelemetry.getPropagators().getTextMapPropagator(),
                    new ReceiveRequestTextMapGetter()));
    Experimental.addOperationListenerAttributesExtractor(
        builder, new RabbitReceiveMessageCountAttributesExtractor());
    setMessagingReceiveExceptionEventExtractor(builder);
    return builder.buildInstrumenter(
        MessagingSpanKindExtractor.create(MessagingOperationType.RECEIVE));
  }

  private static Instrumenter<DeliveryRequest, Void> createDeliverInstrumenter() {
    RabbitDeliveryAttributesGetter getter = new RabbitDeliveryAttributesGetter();
    List<AttributesExtractor<DeliveryRequest, Void>> extractors = new ArrayList<>();
    extractors.add(buildMessagingAttributesExtractor(getter, MessagingOperationType.PROCESS));
    RabbitDeliveryNetAttributesGetter netAttributesGetter = new RabbitDeliveryNetAttributesGetter();
    extractors.add(NetworkAttributesExtractor.create(netAttributesGetter));
    if (emitStableMessagingSemconv()) {
      extractors.add(ServerAttributesExtractor.create(netAttributesGetter));
    }
    extractors.add(new RabbitDeliveryExtraAttributesExtractor());
    if (RabbitInstrumenterHelper.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitDeliveryExperimentalAttributesExtractor());
    }

    SpanNameExtractor<DeliveryRequest> spanNameExtractor =
        emitStableMessagingSemconv()
            ? MessagingSpanNameExtractor.createForOperationType(
                getter, MessagingOperationType.PROCESS)
            : DeliveryRequest::spanName;
    InstrumenterBuilder<DeliveryRequest, Void> builder =
        Instrumenter.<DeliveryRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractors(extractors);
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder,
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator(),
        new DeliveryRequestGetter(),
        false);
  }

  private static <T, V> AttributesExtractor<T, V> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, V> getter, MessagingOperationType operationType) {
    return MessagingAttributesExtractor.builderForOperationType(getter, operationType)
        .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
        .build();
  }

  private RabbitSingletons() {}
}
