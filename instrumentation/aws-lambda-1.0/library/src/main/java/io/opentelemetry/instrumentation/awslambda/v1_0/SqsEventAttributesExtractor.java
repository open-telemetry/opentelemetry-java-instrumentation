/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

class SqsEventAttributesExtractor extends AttributesExtractor<SQSEvent, Void> {
  @Override
  protected void onStart(AttributesBuilder attributes, SQSEvent event) {
    attributes.put(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
    attributes.put(SemanticAttributes.MESSAGING_OPERATION, "process");
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      SQSEvent event,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
