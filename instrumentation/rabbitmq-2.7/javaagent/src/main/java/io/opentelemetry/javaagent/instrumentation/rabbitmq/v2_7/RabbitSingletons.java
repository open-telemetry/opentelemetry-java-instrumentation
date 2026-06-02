/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

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
    return Instrumenter.<ChannelAndMethod, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, ChannelAndMethod::getMethod)
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(
                new RabbitChannelAttributesGetter(), publish ? MessageOperation.PUBLISH : null))
        .addAttributesExtractor(
            NetworkAttributesExtractor.create(new RabbitChannelNetAttributesGetter()))
        .addContextCustomizer(
            (context, request, startAttributes) ->
                context.with(CHANNEL_AND_METHOD_CONTEXT_KEY, new RabbitChannelAndMethodHolder()))
        .buildInstrumenter(channelAndMethod -> publish ? PRODUCER : CLIENT);
  }

  private static Instrumenter<ReceiveRequest, GetResponse> createReceiveInstrumenter() {
    List<AttributesExtractor<ReceiveRequest, GetResponse>> extractors = new ArrayList<>();
    extractors.add(
        buildMessagingAttributesExtractor(
            new RabbitReceiveAttributesGetter(), MessageOperation.RECEIVE));
    extractors.add(NetworkAttributesExtractor.create(new RabbitReceiveNetAttributesGetter()));
    if (RabbitInstrumenterHelper.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitReceiveExperimentalAttributesExtractor());
    }

    return Instrumenter.<ReceiveRequest, GetResponse>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, ReceiveRequest::spanName)
        .addAttributesExtractors(extractors)
        .setEnabled(ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
        .addSpanLinksExtractor(
            new PropagatorBasedSpanLinksExtractor<>(
                GlobalOpenTelemetry.getPropagators().getTextMapPropagator(),
                new ReceiveRequestTextMapGetter()))
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static Instrumenter<DeliveryRequest, Void> createDeliverInstrumenter() {
    List<AttributesExtractor<DeliveryRequest, Void>> extractors = new ArrayList<>();
    extractors.add(
        buildMessagingAttributesExtractor(
            new RabbitDeliveryAttributesGetter(), MessageOperation.PROCESS));
    extractors.add(NetworkAttributesExtractor.create(new RabbitDeliveryNetAttributesGetter()));
    extractors.add(new RabbitDeliveryExtraAttributesExtractor());
    if (RabbitInstrumenterHelper.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitDeliveryExperimentalAttributesExtractor());
    }

    return Instrumenter.<DeliveryRequest, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, DeliveryRequest::spanName)
        .addAttributesExtractors(extractors)
        .buildConsumerInstrumenter(new DeliveryRequestGetter());
  }

  private static <T, V> AttributesExtractor<T, V> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, V> getter, @Nullable MessageOperation operation) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
        .build();
  }

  private RabbitSingletons() {}
}
