/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

public final class ApacheHttpClientNetAttributesGetter extends
    InetSocketAddressNetClientAttributesGetter<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String getTransport(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String getPeerName(ApacheHttpClientRequest request) {
    return request.getPeerName();
  }

  @Override
  public Integer getPeerPort(ApacheHttpClientRequest request) {
    return request.getPeerPort();
  }

  @Nullable
  @Override
  public InetSocketAddress getPeerSocketAddress(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.peerSocketAddress();
  }
}
