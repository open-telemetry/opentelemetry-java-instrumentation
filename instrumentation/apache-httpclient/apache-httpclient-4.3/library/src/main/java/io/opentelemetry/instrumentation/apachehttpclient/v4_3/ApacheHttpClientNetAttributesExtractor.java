/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.http.HttpResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpClientNetAttributesExtractor
    extends NetAttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String transport(ApacheHttpClientRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ApacheHttpClientRequest request) {
    return request.getPeerName();
  }

  @Override
  @Nullable
  public Integer peerPort(ApacheHttpClientRequest request) {
    return request.getPeerPort();
  }

  @Override
  @Nullable
  public String peerIp(ApacheHttpClientRequest request) {
    return null;
  }
}
