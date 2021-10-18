/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

final class GrizzlyNetAttributesExtractor
    extends NetServerAttributesExtractor<HttpRequestPacket, HttpResponsePacket> {

  @Override
  public @Nullable String transport(HttpRequestPacket request) {
    return null;
  }

  @Override
  public @Nullable String peerName(HttpRequestPacket request) {
    return request.getRemoteHost();
  }

  @Override
  public Integer peerPort(HttpRequestPacket request) {
    return request.getRemotePort();
  }

  @Override
  public @Nullable String peerIp(HttpRequestPacket request) {
    return request.getRemoteAddress();
  }
}
