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
  public String transport(Request request, @Nullable ApiResponse<?> response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String peerName(Request request, @Nullable ApiResponse<?> response) {
    return request.url().host();
  }

  @Override
  public Integer peerPort(Request request, @Nullable ApiResponse<?> response) {
    return request.url().port();
  }

  @Nullable
  @Override
  public String peerIp(Request request, @Nullable ApiResponse<?> response) {
    return null;
  }
}
