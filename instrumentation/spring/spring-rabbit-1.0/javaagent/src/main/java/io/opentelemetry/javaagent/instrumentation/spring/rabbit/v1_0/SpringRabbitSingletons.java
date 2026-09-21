/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

public class SpringRabbitSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-rabbit-1.0";

  private static final String PROCESS_OPERATION_NAME = "process";

  private static final Instrumenter<SpringRabbitRequest, Void> instrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    SpringRabbitMessageAttributesGetter getter = new SpringRabbitMessageAttributesGetter();
    SpringRabbitNetAttributesGetter netAttributesGetter = new SpringRabbitNetAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<SpringRabbitRequest, Void> builder =
        Instrumenter.<SpringRabbitRequest, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                new SpringRabbitExtraAttributesExtractor(
                    MessagingAttributesExtractor.builder(
                            getter, operationType, PROCESS_OPERATION_NAME)
                        .setHeaders(ExperimentalConfig.get().getMessagingHeaders())
                        .build()))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter))
            .addOperationMetrics(MessagingProcessMetrics.get())
            .addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    if (emitStableMessagingSemconv()) {
      builder.addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter));
    }
    setMessagingProcessExceptionEventExtractor(builder);

    MessageHeaderGetter headerGetter = new MessageHeaderGetter();
    if (emitStableMessagingSemconv()) {
      builder.addSpanLinksExtractor(
          (links, parentContext, request) -> {
            Set<SpanContext> linked = new HashSet<>();
            for (Message message : request.getMessages()) {
              SpanContext creationContext =
                  Span.fromContext(
                          openTelemetry
                              .getPropagators()
                              .getTextMapPropagator()
                              .extract(Context.root(), message, headerGetter))
                      .getSpanContext();
              if (linked.add(creationContext)) {
                links.addLink(creationContext);
              }
            }
          });
      builder.addContextCustomizer(
          MessagingProcessContextCustomizer.create(
              (parentContext, request) ->
                  openTelemetry
                      .getPropagators()
                      .getTextMapPropagator()
                      .extract(parentContext, request.getMessage(), headerGetter)));
      instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      instrumenter =
          builder.buildConsumerInstrumenter(
              new TextMapGetter<SpringRabbitRequest>() {
                @Override
                public Iterable<String> keys(SpringRabbitRequest request) {
                  return headerGetter.keys(request.getMessage());
                }

                @Override
                @Nullable
                public String get(@Nullable SpringRabbitRequest request, String key) {
                  return request == null ? null : headerGetter.get(request.getMessage(), key);
                }
              });
    }
  }

  public static Instrumenter<SpringRabbitRequest, Void> instrumenter() {
    return instrumenter;
  }

  private SpringRabbitSingletons() {}
}
