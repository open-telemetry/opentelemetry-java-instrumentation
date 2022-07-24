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
import java.util.HashMap;
import java.util.Map;

public class PubsubSingletons {

  private PubsubSingletons() {}

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
    span.setAttribute("messaging.payload", new String(pubsubMessage.getData().toByteArray()));
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
}
