/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

final class ApacheHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String transport(HttpRequest request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(HttpRequest request, @Nullable HttpResponse response) {
    return ApacheHttpClientUtils.getPeerName(request);
  }

  @Override
  public Integer peerPort(HttpRequest request, @Nullable HttpResponse response) {
    return ApacheHttpClientUtils.getPeerPort(request);
  }

  @Override
  @Nullable
  public String peerIp(HttpRequest request, @Nullable HttpResponse response) {
    return null;
  }
}
