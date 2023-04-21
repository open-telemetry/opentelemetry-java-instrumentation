/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

class AwsSdkNetAttributesGetter
    implements NetClientAttributesGetter<ExecutionAttributes, SdkHttpResponse> {

  @Override
  @Nullable
  public String getPeerName(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.host();
  }

  @Override
  public Integer getPeerPort(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.port();
  }
}
