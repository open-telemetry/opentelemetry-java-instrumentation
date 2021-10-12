/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

class AwsSdkNetAttributesExtractor
    extends NetClientAttributesExtractor<ExecutionAttributes, SdkHttpResponse> {
  @Override
  public String transport(ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
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
  public @Nullable String peerIp(ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }
}
