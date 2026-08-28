/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class AkkaHttpServerRequestPeer {
  private static final VirtualField<HttpRequest, InetSocketAddress> REQUEST_PEER =
      VirtualField.find(HttpRequest.class, InetSocketAddress.class);

  static void set(HttpRequest request, InetSocketAddress remoteAddress) {
    REQUEST_PEER.set(request, remoteAddress);
  }

  @Nullable
  static InetSocketAddress get(HttpRequest request) {
    return REQUEST_PEER.get(request);
  }

  private AkkaHttpServerRequestPeer() {}
}
