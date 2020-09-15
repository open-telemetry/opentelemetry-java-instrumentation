/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.api.tracer.utils;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class NetPeerUtils {

  private NetPeerUtils() {}

  public static void setNetPeer(Span span, @Nullable InetSocketAddress remoteConnection) {
    if (remoteConnection != null) {
      InetAddress remoteAddress = remoteConnection.getAddress();
      if (remoteAddress != null) {
        setNetPeer(span, remoteAddress, remoteConnection.getPort());
      } else {
        // Failed DNS lookup, the host string is the name.
        setNetPeer(span, remoteConnection.getHostString(), null, remoteConnection.getPort());
      }
    }
  }

  public static void setNetPeer(Span span, InetAddress remoteAddress, int port) {
    setNetPeer(span, remoteAddress.getHostName(), remoteAddress.getHostAddress(), port);
  }

  public static void setNetPeer(Span span, String nameOrIp, int port) {
    try {
      InetSocketAddress address = new InetSocketAddress(nameOrIp, port);
      setNetPeer(span, address);
    } catch (IllegalArgumentException iae) {
      // can't create address, try setting directly
      setNetPeer(span, nameOrIp, null, port);
    }
  }

  public static void setNetPeer(Span span, String peerName, String peerIp) {
    setNetPeer(span, peerName, peerIp, -1);
  }

  public static void setNetPeer(Span span, String peerName, String peerIp, int port) {

    if (peerName != null && !peerName.equals(peerIp)) {
      SemanticAttributes.NET_PEER_NAME.set(span, peerName);
    }
    if (peerIp != null) {
      SemanticAttributes.NET_PEER_IP.set(span, peerIp);
    }
    String peerService = mapToPeer(peerName);
    if (peerService == null) {
      peerService = mapToPeer(peerIp);
    }
    if (peerService != null) {
      SemanticAttributes.PEER_SERVICE.set(span, peerService);
    }
    if (port > 0) {
      span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), port);
    }
  }

  private static String mapToPeer(String endpoint) {
    if (endpoint == null) {
      return null;
    }

    return Config.get().getEndpointPeerServiceMapping().get(endpoint);
  }
}
