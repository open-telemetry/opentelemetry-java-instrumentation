/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

class AwsSdkRpcAttributesGetter implements RpcAttributesGetter<ExecutionAttributes, Response> {

  @Override
  public String getSystem(ExecutionAttributes request) {
    return "aws-api";
  }

  @Override
  public String getService(ExecutionAttributes request) {
    return request.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
  }

  @Deprecated
  @Override
  public String getMethod(ExecutionAttributes request) {
    return request.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
  }

  @Nullable
  @Override
  public String getErrorType(
      ExecutionAttributes request, @Nullable Response response, @Nullable Throwable error) {
    if (error instanceof AwsServiceException) {
      AwsErrorDetails errorDetails = ((AwsServiceException) error).awsErrorDetails();
      if (errorDetails != null) {
        String errorCode = errorDetails.errorCode();
        if (errorCode != null && !errorCode.isEmpty()) {
          return errorCode;
        }
      }
    }
    return null;
  }
}
