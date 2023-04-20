/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

final class PlayWsClientNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<Request, Response> {

  @Nullable
  @Override
  public String getPeerName(Request request) {
    return request.getUri().getHost();
  }

  @Override
  public Integer getPeerPort(Request request) {
    return request.getUri().getPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(Request request, @Nullable Response response) {
    if (response != null && response.getRemoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) response.getRemoteAddress();
    }
    return null;
  }
}
