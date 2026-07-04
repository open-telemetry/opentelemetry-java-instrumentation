/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessage;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessageImpl;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsParentContext;
import io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.SpringAwsUtil.TracingContext;
import java.util.Collection;
import org.springframework.messaging.Message;

public final class SpringAwsSqsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-cloud-aws-3.0";

  private static final SpringAwsSqsBatchProcessAttributesGetter BATCH_ATTRIBUTES_GETTER =
      new SpringAwsSqsBatchProcessAttributesGetter();

  private static final VirtualField<Message<?>, TracingContext> tracingContextField =
      VirtualField.find(Message.class, TracingContext.class);

  private static final Instrumenter<Collection<Message<?>>, Void> BATCH_PROCESS_INSTRUMENTER =
      Instrumenter.<Collection<Message<?>>, Void>builder(
              GlobalOpenTelemetry.get(),
              INSTRUMENTATION_NAME,
              MessagingSpanNameExtractor.create(BATCH_ATTRIBUTES_GETTER, MessageOperation.PROCESS))
          .addAttributesExtractor(
              MessagingAttributesExtractor.builder(
                      BATCH_ATTRIBUTES_GETTER, MessageOperation.PROCESS)
                  .build())
          .addSpanLinksExtractor(
              (spanLinks, parentContext, messages) -> {
                for (Message<?> message : messages) {
                  TracingContext tracingContext = tracingContextField.get(message);
                  if (tracingContext != null) {
                    SqsMessage wrappedMessage = SqsMessageImpl.wrap(tracingContext.getSqsMessage());
                    Context extracted =
                        SqsParentContext.ofMessage(wrappedMessage, tracingContext.getConfig());
                    spanLinks.addLink(Span.fromContext(extracted).getSpanContext());
                  }
                }
              })
          .buildInstrumenter(SpanKindExtractor.alwaysConsumer());

  public static Instrumenter<Collection<Message<?>>, Void> batchProcessInstrumenter() {
    return BATCH_PROCESS_INSTRUMENTER;
  }

  private SpringAwsSqsSingletons() {}
}
