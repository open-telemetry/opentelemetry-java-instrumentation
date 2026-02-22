/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.ArrayList;
import java.util.List;

public final class RabbitSingletons {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "rabbitmq")
          .getBoolean("experimental_span_attributes/development", false);
  private static final String instrumentationName = "io.opentelemetry.rabbitmq-2.7";
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
    InstrumenterBuilder<ChannelAndMethod, Void> builder =
        Instrumenter.<ChannelAndMethod, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, ChannelAndMethod::getMethod)
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    RabbitChannelAttributesGetter.INSTANCE,
                    publish ? MessageOperation.PUBLISH : null))
            .addAttributesExtractor(
                NetworkAttributesExtractor.create(new RabbitChannelNetAttributesGetter()))
            .addContextCustomizer(
                (context, request, startAttributes) ->
                    context.with(
                        CHANNEL_AND_METHOD_CONTEXT_KEY, new RabbitChannelAndMethodHolder()));
    Experimental.setExceptionEventExtractor(builder, MessagingExceptionEventExtractors.client());
    return builder.buildInstrumenter(channelAndMethod -> publish ? PRODUCER : CLIENT);
  }

  private static Instrumenter<ReceiveRequest, GetResponse> createReceiveInstrumenter() {
    List<AttributesExtractor<ReceiveRequest, GetResponse>> extractors = new ArrayList<>();
    extractors.add(
        buildMessagingAttributesExtractor(
            RabbitReceiveAttributesGetter.INSTANCE, MessageOperation.RECEIVE));
    extractors.add(NetworkAttributesExtractor.create(new RabbitReceiveNetAttributesGetter()));
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitReceiveExperimentalAttributesExtractor());
    }

    InstrumenterBuilder<ReceiveRequest, GetResponse> builder =
        Instrumenter.<ReceiveRequest, GetResponse>builder(
                GlobalOpenTelemetry.get(), instrumentationName, ReceiveRequest::spanName)
            .addAttributesExtractors(extractors)
            .setEnabled(ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
            .addSpanLinksExtractor(
                new PropagatorBasedSpanLinksExtractor<>(
                    GlobalOpenTelemetry.getPropagators().getTextMapPropagator(),
                    ReceiveRequestTextMapGetter.INSTANCE));
    Experimental.setExceptionEventExtractor(builder, MessagingExceptionEventExtractors.client());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static Instrumenter<DeliveryRequest, Void> createDeliverInstrumenter() {
    List<AttributesExtractor<DeliveryRequest, Void>> extractors = new ArrayList<>();
    extractors.add(
        buildMessagingAttributesExtractor(
            RabbitDeliveryAttributesGetter.INSTANCE, MessageOperation.PROCESS));
    extractors.add(NetworkAttributesExtractor.create(new RabbitDeliveryNetAttributesGetter()));
    extractors.add(new RabbitDeliveryExtraAttributesExtractor());
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitDeliveryExperimentalAttributesExtractor());
    }

    InstrumenterBuilder<DeliveryRequest, Void> builder =
        Instrumenter.<DeliveryRequest, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, DeliveryRequest::spanName)
            .addAttributesExtractors(extractors);
    Experimental.setExceptionEventExtractor(builder, MessagingExceptionEventExtractors.process());
    return builder.buildConsumerInstrumenter(DeliveryRequestGetter.INSTANCE);
  }

  private static <T, V> AttributesExtractor<T, V> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, V> getter, MessageOperation operation) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
        .build();
  }

  private RabbitSingletons() {}
}
