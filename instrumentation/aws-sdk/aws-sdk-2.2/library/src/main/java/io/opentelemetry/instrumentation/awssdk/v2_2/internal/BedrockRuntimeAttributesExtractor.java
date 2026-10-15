/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

class BedrockRuntimeAttributesExtractor
    implements AttributesExtractor<ExecutionAttributes, Response> {

  // copied from AwsIncubatingAttributes
  private static final AttributeKey<String> AWS_BEDROCK_GUARDRAIL_ID =
      stringKey("aws.bedrock.guardrail.id");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ExecutionAttributes executionAttributes) {
    attributes.put(
        AWS_BEDROCK_GUARDRAIL_ID, BedrockRuntimeAccess.getGuardrailIdentifier(executionAttributes));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ExecutionAttributes executionAttributes,
      @Nullable Response response,
      @Nullable Throwable error) {}
}
