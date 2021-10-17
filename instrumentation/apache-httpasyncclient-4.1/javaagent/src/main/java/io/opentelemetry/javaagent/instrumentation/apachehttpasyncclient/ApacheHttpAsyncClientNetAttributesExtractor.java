/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

final class ApacheHttpAsyncClientNetAttributesExtractor
    extends NetClientAttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String transport(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getPeerName();
  }

  @Override
  public Integer peerPort(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getPeerPort();
  }

  @Override
  @Nullable
  public String peerIp(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }
}
