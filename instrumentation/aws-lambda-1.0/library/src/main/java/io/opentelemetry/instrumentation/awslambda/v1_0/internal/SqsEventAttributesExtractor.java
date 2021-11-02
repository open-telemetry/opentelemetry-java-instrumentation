/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

class SqsEventAttributesExtractor implements AttributesExtractor<SQSEvent, Void> {
  @Override
  public void onStart(AttributesBuilder attributes, SQSEvent event) {
    attributes.put(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
    attributes.put(SemanticAttributes.MESSAGING_OPERATION, "process");
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      SQSEvent event,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
