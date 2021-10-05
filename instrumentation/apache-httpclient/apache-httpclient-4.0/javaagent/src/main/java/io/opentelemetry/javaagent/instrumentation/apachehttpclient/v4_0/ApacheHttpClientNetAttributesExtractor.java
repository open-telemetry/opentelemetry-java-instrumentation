/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesClientExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.http.HttpResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpClientNetAttributesExtractor
    extends NetAttributesClientExtractor<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String transport(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getPeerName();
  }

  @Override
  public Integer peerPort(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getPeerPort();
  }

  @Override
  public @Nullable String peerIp(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }
}
