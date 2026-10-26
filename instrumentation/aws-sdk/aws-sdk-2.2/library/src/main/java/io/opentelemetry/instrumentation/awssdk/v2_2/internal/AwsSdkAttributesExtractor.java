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
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

class AwsSdkAttributesExtractor implements AttributesExtractor<ExecutionAttributes, Response> {
  private static final AttributeKey<String> RPC_SYSTEM = stringKey("rpc.system");
  private static final AttributeKey<String> RPC_SERVICE = stringKey("rpc.service");
  private static final AttributeKey<String> RPC_METHOD = stringKey("rpc.method");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ExecutionAttributes request) {
    attributes.put(RPC_SYSTEM, "aws-api");
    attributes.put(RPC_SERVICE, request.getAttribute(SdkExecutionAttribute.SERVICE_NAME));
    attributes.put(RPC_METHOD, request.getAttribute(SdkExecutionAttribute.OPERATION_NAME));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ExecutionAttributes request,
      @Nullable Response response,
      @Nullable Throwable error) {}
}
