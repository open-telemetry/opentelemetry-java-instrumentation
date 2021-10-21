/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.ArrayList;
import java.util.List;

public class RabbitSingletons {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.rabbitmq.experimental-span-attributes", false);
  private static final String instrumentationName = "io.opentelemetry.rabbitmq-2.7";
  private static final Instrumenter<ChannelAndMethod, Void> channelInstrumenter;
  private static final Instrumenter<ReceiveRequest, GetResponse> receiveInstrumenter;
  private static final Instrumenter<DeliveryRequest, Void> deliverInstrumenter;

  static {
    channelInstrumenter = createChanneInstrumenter();
    receiveInstrumenter = createReceiveInstrumenter();
    deliverInstrumenter = createDeliverInstrumenter();
  }

  public static Instrumenter<ChannelAndMethod, Void> channelInstrumenter() {
    return channelInstrumenter;
  }

  public static Instrumenter<ReceiveRequest, GetResponse> receiveInstrumenter() {
    return receiveInstrumenter;
  }

  static Instrumenter<DeliveryRequest, Void> deliverInstrumenter() {
    return deliverInstrumenter;
  }

  private static Instrumenter<ChannelAndMethod, Void> createChanneInstrumenter() {
    return Instrumenter.<ChannelAndMethod, Void>newBuilder(
            GlobalOpenTelemetry.get(), instrumentationName, ChannelAndMethod::getMethod)
        .addAttributesExtractors(
            new RabbitChannelAttributesExtractor(), new RabbitChannelNetAttributesExtractor())
        .newInstrumenter(
            channelAndMethod ->
                channelAndMethod.getMethod().equals("Channel.basicPublish") ? PRODUCER : CLIENT);
  }

  private static Instrumenter<ReceiveRequest, GetResponse> createReceiveInstrumenter() {
    List<AttributesExtractor<ReceiveRequest, GetResponse>> extractors = new ArrayList<>();
    extractors.add(new RabbitReceiveAttributesExtractor());
    extractors.add(new RabbitReceiveNetAttributesExtractor());
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitReceiveExperimentalAttributesExtractor());
    }

    return Instrumenter.<ReceiveRequest, GetResponse>newBuilder(
            GlobalOpenTelemetry.get(), instrumentationName, ReceiveRequest::spanName)
        .addAttributesExtractors(extractors)
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private static Instrumenter<DeliveryRequest, Void> createDeliverInstrumenter() {
    List<AttributesExtractor<DeliveryRequest, Void>> extractors = new ArrayList<>();
    extractors.add(new RabbitDeliveryAttributesExtractor());
    extractors.add(new RabbitDeliveryExtraAttributesExtractor());
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      extractors.add(new RabbitDeliveryExperimentalAttributesExtractor());
    }

    return Instrumenter.<DeliveryRequest, Void>newBuilder(
            GlobalOpenTelemetry.get(), instrumentationName, DeliveryRequest::spanName)
        .addAttributesExtractors(extractors)
        .newConsumerInstrumenter(TextMapExtractAdapter.GETTER);
  }
}
