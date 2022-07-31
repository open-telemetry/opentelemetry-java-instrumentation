/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PubsubSingletons {

  private PubsubSingletons() {}

  private static final String MESSAGE_PAYLOAD_ATTRIBUTE = "messaging.payload";
  private static final String ATTRIBUTES_FIELD_NAME = "attributes_";
  private static final String MAP_DATA_FIELD_NAME = "mapData";
  private static final String DELEGATE_DATA_FIELD_NAME = "delegate";

  public static final String instrumentationName = "io.opentelemetry.pubsub-1.101.0";

  public static final String publisherSpanName = "pubsub.publish";
  public static final String subscriberSpanName = "pubsub.subscribe";
  private static final Instrumenter<PubsubMessage, Void> publisherInstrumenter;
  private static final Instrumenter<PubsubMessage, Void> subscriberInstrumenter;

  static {
    publisherInstrumenter = createPublisherInstrumenter();
    subscriberInstrumenter = createSubscriberInstrumenter();
  }

  public static Instrumenter<PubsubMessage, Void> publisherInstrumenter() {
    return publisherInstrumenter;
  }

  private static Instrumenter<PubsubMessage, Void> createPublisherInstrumenter() {
    SpanNameExtractor publisherSpanNameExtractor =
        new SpanNameExtractor() {
          @Override
          public String extract(Object o) {
            return publisherSpanName;
          }
        };

    return Instrumenter.<PubsubMessage, Void>builder(
            GlobalOpenTelemetry.get(), instrumentationName, publisherSpanNameExtractor)
        .newInstrumenter(SpanKindExtractor.alwaysProducer());
  }

  public static Instrumenter<PubsubMessage, Void> createSubscriberInstrumenter() {

    SpanNameExtractor subscriberSpanNameExtractor =
        new SpanNameExtractor() {
          @Override
          public String extract(Object o) {
            return subscriberSpanName;
          }
        };

    return Instrumenter.<PubsubMessage, Void>builder(
            GlobalOpenTelemetry.get(), instrumentationName, subscriberSpanNameExtractor)
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static void startAndInjectSpan(Context parentContext, PubsubMessage pubsubMessage) {
    if (!publisherInstrumenter().shouldStart(parentContext, pubsubMessage)) {
      return;
    }

    Map<String, String> newAttrMap = new HashMap<>();
    newAttrMap.putAll(pubsubMessage.getAttributesMap());

    Context context = publisherInstrumenter().start(parentContext, pubsubMessage);
    Span span = Java8BytecodeBridge.spanFromContext(context);
    span.setAttribute(MESSAGE_PAYLOAD_ATTRIBUTE, new String(pubsubMessage.getData().toByteArray()));
    GlobalOpenTelemetry.get()
        .getPropagators()
        .getTextMapPropagator()
        .inject(context, pubsubMessage, PubSubAttributesMapSetter.INSTANCE);
    publisherInstrumenter().end(context, pubsubMessage, null, null);
  }

  public static void buildAndFinishSpan(Context context, PubsubMessage pubsubMessage) {
    Context linkedContext =
        GlobalOpenTelemetry.get()
            .getPropagators()
            .getTextMapPropagator()
            .extract(context, pubsubMessage, PubSubAttributesMapGetter.INSTANCE);
    Context newContext = context.with(Span.fromContext(linkedContext));

    if (!subscriberInstrumenter.shouldStart(newContext, pubsubMessage)) {
      return;
    }
    Context current = subscriberInstrumenter.start(newContext, pubsubMessage);
    subscriberInstrumenter.end(current, pubsubMessage, null, null);
  }

  public static Optional<Object> extractPubsubMessageAttributes(PubsubMessage pubsubMessage) {
    try {
      Class cls = pubsubMessage.getClass();
      Field attributes = cls.getDeclaredField(ATTRIBUTES_FIELD_NAME);
      attributes.setAccessible(true);
      Class attributesClass = attributes.get(pubsubMessage).getClass();
      Field mapData = attributesClass.getDeclaredField(MAP_DATA_FIELD_NAME);
      mapData.setAccessible(true);
      Class mapDataObj = mapData.get(attributes.get(pubsubMessage)).getClass();

      Field delegateField = mapDataObj.getDeclaredField(DELEGATE_DATA_FIELD_NAME);
      delegateField.setAccessible(true);
      return (Optional<Object>) delegateField.get(mapData.get(attributes.get(pubsubMessage)));

    } catch (Exception e) {
      System.out.println("Got Exception while instrumenting pubsubMessage: " + e);

    }
    return Optional.empty();
  }
}
