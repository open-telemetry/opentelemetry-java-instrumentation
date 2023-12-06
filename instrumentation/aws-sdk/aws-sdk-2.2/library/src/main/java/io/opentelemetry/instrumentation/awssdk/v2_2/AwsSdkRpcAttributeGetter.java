/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributeGetter;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

enum AwsSdkRpcAttributeGetter implements RpcAttributeGetter<ExecutionAttributes> {
  INSTANCE;

  @Override
  public String getSystem(ExecutionAttributes request) {
    return "aws-api";
  }

  @Override
  public String getService(ExecutionAttributes request) {
    return request.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
  }

  @Override
  public String getMethod(ExecutionAttributes request) {
    return request.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
  }
}
