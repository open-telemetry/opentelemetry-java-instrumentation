/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.SEND;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
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
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators.DecoratorRegistry;
import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.util.StringHelper;

class CamelSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.camel-2.20";

  private static final DecoratorRegistry registry = new DecoratorRegistry();
  private static final Instrumenter<CamelRequest, Void> instrumenter = createInstrumenter();
  private static final Instrumenter<CamelRequest, Void> messagingSendInstrumenter =
      createMessagingInstrumenter(SEND, "send", true, true);
  private static final Instrumenter<CamelRequest, Void> messagingPublishInstrumenter =
      createMessagingInstrumenter(SEND, "publish", true, true);
  // AWS SQS sends rely on the nested AWS SDK producer span to inject propagation, while Camel owns
  // the messaging operation metrics.
  private static final Instrumenter<CamelRequest, Void> keylessMessagingSendInstrumenter =
      createMessagingInstrumenter(SEND, "send", false, true);
  private static final Instrumenter<CamelRequest, Void> messagingProcessInstrumenter =
      createMessagingInstrumenter(PROCESS, "process", true, false);

  private static Instrumenter<CamelRequest, Void> createInstrumenter() {
    SpanNameExtractor<CamelRequest> spanNameExtractor =
        camelRequest ->
            camelRequest
                .getSpanDecorator()
                .getOperationName(
                    camelRequest.getExchange(),
                    camelRequest.getEndpoint(),
                    camelRequest.getCamelDirection());

    return instrumenterBuilder(spanNameExtractor).buildInstrumenter(CamelRequest::getSpanKind);
  }

  private static Instrumenter<CamelRequest, Void> createMessagingInstrumenter(
      MessagingOperationType operationType,
      String operationName,
      boolean exposeSpanKey,
      boolean recordProducerMetrics) {
    MessagingAttributesGetter<CamelRequest, Void> getter = new CamelMessagingAttributesGetter();
    SpanNameExtractor<CamelRequest> legacySpanNameExtractor =
        request ->
            request
                .getSpanDecorator()
                .getOperationName(
                    request.getExchange(), request.getEndpoint(), request.getCamelDirection());
    SpanNameExtractor<CamelRequest> spanNameExtractor =
        emitStableMessagingSemconv()
            ? MessagingSpanNameExtractor.create(getter, operationType, operationName)
            : legacySpanNameExtractor;
    InstrumenterBuilder<CamelRequest, Void> builder = instrumenterBuilder(spanNameExtractor);
    if (emitStableMessagingSemconv()) {
      AttributesExtractor<CamelRequest, Void> attributesExtractor =
          MessagingAttributesExtractor.create(getter, operationType, operationName);
      builder.addAttributesExtractor(
          exposeSpanKey
              ? attributesExtractor
              : new KeylessAttributesExtractor(attributesExtractor));
    }

    if (operationType == SEND && recordProducerMetrics) {
      builder.addOperationMetrics(MessagingProducerMetrics.getForOperationType());
    }
    if (operationType == PROCESS && emitStableMessagingSemconv()) {
      builder.addOperationMetrics(MessagingProcessMetrics.get());
      builder.addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
      return MessagingProcessInstrumenterFactory.create(
          builder,
          CamelPropagationUtil.messagingPropagator(),
          CamelPropagationUtil.messagingGetter(),
          false);
    }
    if (emitStableMessagingSemconv()) {
      return builder.buildInstrumenter(
          MessagingSpanKindExtractor.create(
              operationType, CamelRequest::isMessagingSpanContextPropagated));
    }
    return builder.buildInstrumenter(CamelRequest::getSpanKind);
  }

  private static InstrumenterBuilder<CamelRequest, Void> instrumenterBuilder(
      SpanNameExtractor<CamelRequest> spanNameExtractor) {
    SpanStatusExtractor<CamelRequest, Void> spanStatusExtractor =
        (spanStatusBuilder, request, unused, error) -> {
          if (request.getExchange().isFailed()) {
            spanStatusBuilder.setStatus(StatusCode.ERROR);
          }
        };
    return Instrumenter.<CamelRequest, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractor(new CamelAttributesExtractor())
        .setSpanStatusExtractor(spanStatusExtractor);
  }

  static Instrumenter<CamelRequest, Void> instrumenter(CamelRequest request) {
    if (request.isMessaging()) {
      if (request.getCamelDirection() == CamelDirection.OUTBOUND) {
        if (!request.isMessagingSpanContextPropagated()) {
          return keylessMessagingSendInstrumenter;
        }
        return "publish".equals(request.getMessagingSendOperationName())
            ? messagingPublishInstrumenter
            : messagingSendInstrumenter;
      }
      return messagingProcessInstrumenter;
    }
    return instrumenter;
  }

  static SpanDecorator getSpanDecorator(Endpoint endpoint) {
    String component = "";
    String uri = endpoint.getEndpointUri();
    String[] splitUri = StringHelper.splitOnCharacter(uri, ":", 2);
    if (splitUri[1] != null) {
      component = splitUri[0];
    }
    return registry.forComponent(component);
  }

  private static class CamelAttributesExtractor implements AttributesExtractor<CamelRequest, Void> {

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, CamelRequest camelRequest) {
      SpanDecorator spanDecorator = camelRequest.getSpanDecorator();
      spanDecorator.pre(
          attributes,
          camelRequest.getExchange(),
          camelRequest.getEndpoint(),
          camelRequest.getCamelDirection());
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        CamelRequest camelRequest,
        @Nullable Void unused,
        @Nullable Throwable error) {
      SpanDecorator spanDecorator = camelRequest.getSpanDecorator();
      spanDecorator.post(attributes, camelRequest.getExchange(), camelRequest.getEndpoint());
    }
  }

  private static class KeylessAttributesExtractor
      implements AttributesExtractor<CamelRequest, Void> {

    private final AttributesExtractor<CamelRequest, Void> delegate;

    private KeylessAttributesExtractor(AttributesExtractor<CamelRequest, Void> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, CamelRequest request) {
      delegate.onStart(attributes, parentContext, request);
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        CamelRequest request,
        @Nullable Void unused,
        @Nullable Throwable error) {
      delegate.onEnd(attributes, context, request, null, error);
    }
  }

  private CamelSingletons() {}
}
