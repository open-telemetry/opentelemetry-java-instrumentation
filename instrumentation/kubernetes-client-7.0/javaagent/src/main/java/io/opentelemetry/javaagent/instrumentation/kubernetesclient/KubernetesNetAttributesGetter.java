/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import okhttp3.Request;

class KubernetesNetAttributesGetter implements NetClientAttributesGetter<Request, ApiResponse<?>> {

  @Override
  public String getTransport(Request request, @Nullable ApiResponse<?> response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getProtocolName(Request request, @Nullable ApiResponse<?> response) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(Request request, @Nullable ApiResponse<?> response) {
    return null;
  }

  @Override
  public String getPeerName(Request request) {
    return request.url().host();
  }

  @Override
  public Integer getPeerPort(Request request) {
    return request.url().port();
  }
}
