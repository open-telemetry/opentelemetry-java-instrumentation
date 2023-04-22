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
  public String getPeerName(HttpClientRequest request) {
    return null;
  }

  @Override
  public Integer getPeerPort(HttpClientRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getSockPeerName(HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    SocketAddress socketAddress = response.netSocket().remoteAddress();
    return socketAddress == null ? null : socketAddress.host();
  }

  @Nullable
  @Override
  public Integer getSockPeerPort(HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    SocketAddress socketAddress = response.netSocket().remoteAddress();
    return socketAddress == null ? null : socketAddress.port();
  }
}
