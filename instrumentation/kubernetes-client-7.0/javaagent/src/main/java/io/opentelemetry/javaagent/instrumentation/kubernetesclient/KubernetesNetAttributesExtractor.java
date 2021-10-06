/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import okhttp3.Request;
import org.checkerframework.checker.nullness.qual.Nullable;

class KubernetesNetAttributesExtractor
    extends NetServerAttributesExtractor<Request, ApiResponse<?>> {
  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String peerName(Request request) {
    return request.url().host();
  }

  @Override
  public Integer peerPort(Request request) {
    return request.url().port();
  }

  @Override
  public @Nullable String peerIp(Request request) {
    return null;
  }
}
