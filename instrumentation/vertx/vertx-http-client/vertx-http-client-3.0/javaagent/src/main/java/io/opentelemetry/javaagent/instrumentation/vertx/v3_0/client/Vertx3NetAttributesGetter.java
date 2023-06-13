/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.net.SocketAddress;
import javax.annotation.Nullable;

enum Vertx3NetAttributesGetter
    implements NetClientAttributesGetter<HttpClientRequest, HttpClientResponse> {
  INSTANCE;

  @Nullable
  @Override
  public String getServerAddress(HttpClientRequest request) {
    return null;
  }

  @Override
  public Integer getServerPort(HttpClientRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getServerSocketDomain(
      HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    SocketAddress socketAddress = response.netSocket().remoteAddress();
    return socketAddress == null ? null : socketAddress.host();
  }

  @Nullable
  @Override
  public Integer getServerSocketPort(
      HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    SocketAddress socketAddress = response.netSocket().remoteAddress();
    return socketAddress == null ? null : socketAddress.port();
  }
}
