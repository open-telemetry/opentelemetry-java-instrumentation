/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import javax.annotation.Nullable;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

final class GrizzlyNetAttributesExtractor
    extends NetServerAttributesExtractor<HttpRequestPacket, HttpResponsePacket> {

  @Nullable
  @Override
  public String transport(HttpRequestPacket request) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(HttpRequestPacket request) {
    return request.getRemoteHost();
  }

  @Override
  public Integer peerPort(HttpRequestPacket request) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String peerIp(HttpRequestPacket request) {
    return request.getRemoteAddress();
  }
}
