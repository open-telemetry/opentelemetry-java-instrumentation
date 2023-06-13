/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

final class PlayWsClientNetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Nullable
  @Override
  public String getServerAddress(Request request) {
    return request.getUri().getHost();
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.getUri().getPort();
  }

  @Override
  @Nullable
  public InetSocketAddress getServerInetSocketAddress(
      Request request, @Nullable Response response) {
    if (response != null && response.getRemoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) response.getRemoteAddress();
    }
    return null;
  }
}
