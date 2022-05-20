/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.DecoratorRegistry;
import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.util.StringHelper;

public final class CamelSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-camel-2.20";

  private static final DecoratorRegistry registry = new DecoratorRegistry();
  private static final Instrumenter<CamelRequest, Void> INSTRUMENTER;

  static {
    SpanNameExtractor<CamelRequest> spanNameExtractor =
        camelRequest ->
            camelRequest
                .getSpanDecorator()
                .getOperationName(
                    camelRequest.getExchange(),
                    camelRequest.getEndpoint(),
                    camelRequest.getCamelDirection());

    AttributesExtractor<CamelRequest, Void> attributesExtractor =
        new AttributesExtractor<CamelRequest, Void>() {

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
        };

    SpanStatusExtractor<CamelRequest, Void> spanStatusExtractor =
        (spanStatusBuilder, request, unused, error) -> {
          if (request.getExchange().isFailed()) {
            spanStatusBuilder.setStatus(StatusCode.ERROR);
          }
        };

    InstrumenterBuilder<CamelRequest, Void> builder =
        Instrumenter.builder(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor);
    builder.addAttributesExtractor(attributesExtractor);
    builder.setSpanStatusExtractor(spanStatusExtractor);

    INSTRUMENTER = builder.newInstrumenter(request -> request.getSpanKind());
  }

  public static Instrumenter<CamelRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static SpanDecorator getSpanDecorator(Endpoint endpoint) {
    String component = "";
    String uri = endpoint.getEndpointUri();
    String[] splitUri = StringHelper.splitOnCharacter(uri, ":", 2);
    if (splitUri[1] != null) {
      component = splitUri[0];
    }
    return registry.forComponent(component);
  }

  private CamelSingletons() {}
}
