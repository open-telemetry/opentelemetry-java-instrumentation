/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

final class ApacheHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String getNetworkProtocolName(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getProtocolName();
  }

  @Override
  public String getNetworkProtocolVersion(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getProtocolVersion();
  }

  @Override
  @Nullable
  public String getServerAddress(ApacheHttpClientRequest request) {
    return request.getServerAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(ApacheHttpClientRequest request) {
    return request.getServerPort();
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getServerSocketAddress();
  }
}
