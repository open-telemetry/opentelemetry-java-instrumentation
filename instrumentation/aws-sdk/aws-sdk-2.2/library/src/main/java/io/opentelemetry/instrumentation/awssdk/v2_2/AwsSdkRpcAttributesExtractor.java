/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpResponse;

final class AwsSdkRpcAttributesExtractor
    extends RpcAttributesExtractor<ExecutionAttributes, SdkHttpResponse> {

  @Override
  protected String system(ExecutionAttributes request) {
    return "aws-api";
  }

  @Override
  protected String service(ExecutionAttributes request) {
    return request.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
  }

  @Override
  protected String method(ExecutionAttributes request) {
    return request.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
  }
}
