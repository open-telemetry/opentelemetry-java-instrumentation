/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;

import com.google.cloud.spring.pubsub.support.converter.ConvertedBasicAcknowledgeablePubsubMessage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public final class SpringCloudGcpSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-cloud-gcp-5.0";
  private static final String PROCESS_OPERATION_NAME = "process";

  private static final Instrumenter<ConvertedBasicAcknowledgeablePubsubMessage<?>, Void>
      instrumenter = createInstrumenter(GlobalOpenTelemetry.get());

  private static Instrumenter<ConvertedBasicAcknowledgeablePubsubMessage<?>, Void>
      createInstrumenter(OpenTelemetry openTelemetry) {
    SpringCloudGcpMessageAttributesGetter getter = new SpringCloudGcpMessageAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;
    InstrumenterBuilder<ConvertedBasicAcknowledgeablePubsubMessage<?>, Void> builder =
        Instrumenter.<ConvertedBasicAcknowledgeablePubsubMessage<?>, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(getter, operationType, PROCESS_OPERATION_NAME)
                    .setHeaders(ExperimentalConfig.get().getMessagingHeaders())
                    .build())
            .addOperationMetrics(MessagingProcessMetrics.get());
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        new MessageHeaderGetter(),
        ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());
  }

  public static Instrumenter<ConvertedBasicAcknowledgeablePubsubMessage<?>, Void> instrumenter() {
    return instrumenter;
  }

  private SpringCloudGcpSingletons() {}
}
