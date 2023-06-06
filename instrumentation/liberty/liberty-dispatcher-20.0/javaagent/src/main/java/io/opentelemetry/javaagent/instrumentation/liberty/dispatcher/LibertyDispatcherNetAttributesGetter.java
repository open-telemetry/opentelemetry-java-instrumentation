/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;

public class LibertyDispatcherNetAttributesGetter
    implements NetServerAttributesGetter<LibertyRequest, LibertyResponse> {

  @Nullable
  @Override
  public String getNetworkProtocolName(
      LibertyRequest request, @Nullable LibertyResponse libertyResponse) {
    String protocol = request.getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      LibertyRequest request, @Nullable LibertyResponse libertyResponse) {
    String protocol = request.getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(LibertyRequest request) {
    return request.request().getURLHost();
  }

  @Override
  public Integer getServerPort(LibertyRequest request) {
    return request.request().getURLPort();
  }

  @Override
  @Nullable
  public String getSockPeerAddr(LibertyRequest request) {
    return request.dispatcher().getRemoteHostAddress();
  }

  @Override
  public Integer getSockPeerPort(LibertyRequest request) {
    return request.dispatcher().getRemotePort();
  }

  @Nullable
  @Override
  public String getServerSocketAddress(LibertyRequest request, LibertyResponse response) {
    return request.dispatcher().getLocalHostAddress();
  }

  @Nullable
  @Override
  public Integer getServerSocketPort(LibertyRequest request, LibertyResponse response) {
    return request.dispatcher().getLocalPort();
  }
}
