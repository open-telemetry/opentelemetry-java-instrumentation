/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;
import org.glassfish.grizzly.http.HttpRequestPacket;

final class GrizzlyNetAttributesGetter implements NetServerAttributesGetter<HttpRequestPacket> {

  @Nullable
  @Override
  public String transport(HttpRequestPacket request) {
    return null;
  }

  @Override
  public Integer sockPeerPort(HttpRequestPacket request) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String sockPeerAddr(HttpRequestPacket request) {
    return request.getRemoteAddress();
  }
}
