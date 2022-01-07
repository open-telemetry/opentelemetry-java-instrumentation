/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesAdapter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

class AwsSdkNetAttributesAdapter
    implements NetClientAttributesAdapter<ExecutionAttributes, SdkHttpResponse> {

  @Override
  public String transport(ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.host();
  }

  @Override
  public Integer peerPort(ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.port();
  }

  @Override
  @Nullable
  public String peerIp(ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }
}
