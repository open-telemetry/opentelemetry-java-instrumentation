/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.utils;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class NetPeerUtils {

  public static final NetPeerUtils INSTANCE = new NetPeerUtils(Config.get());

  private final Map<String, String> peerServiceMapping;

  // visible for testing
  NetPeerUtils(Config config) {
    this.peerServiceMapping =
        Collections.unmodifiableMap(
            config.getMapProperty("otel.instrumentation.common.peer-service-mapping"));
  }

  public void setNetPeer(Span span, @Nullable InetSocketAddress remoteConnection) {
    setNetPeer(span::setAttribute, remoteConnection);
  }

  public void setNetPeer(SpanBuilder span, @Nullable InetSocketAddress remoteConnection) {
    setNetPeer(span::setAttribute, remoteConnection);
  }

  public void setNetPeer(SpanAttributeSetter span, @Nullable InetSocketAddress remoteConnection) {
    if (remoteConnection != null) {
      InetAddress remoteAddress = remoteConnection.getAddress();
      if (remoteAddress != null) {
        setNetPeer(
            span,
            remoteAddress.getHostName(),
            remoteAddress.getHostAddress(),
            remoteConnection.getPort());
      } else {
        // Failed DNS lookup, the host string is the name.
        setNetPeer(span, remoteConnection.getHostString(), null, remoteConnection.getPort());
      }
    }
  }

  public void setNetPeer(SpanBuilder span, InetAddress remoteAddress, int port) {
    setNetPeer(
        span::setAttribute, remoteAddress.getHostName(), remoteAddress.getHostAddress(), port);
  }

  public void setNetPeer(Span span, String nameOrIp, int port) {
    try {
      InetSocketAddress address = new InetSocketAddress(nameOrIp, port);
      setNetPeer(span, address);
    } catch (IllegalArgumentException iae) {
      // can't create address, try setting directly
      setNetPeer(span::setAttribute, nameOrIp, null, port);
    }
  }

  public void setNetPeer(Span span, String peerName, String peerIp) {
    setNetPeer(span::setAttribute, peerName, peerIp, -1);
  }

  public void setNetPeer(Span span, String peerName, String peerIp, int port) {
    setNetPeer(span::setAttribute, peerName, peerIp, port);
  }

  public void setNetPeer(SpanAttributeSetter span, String peerName, String peerIp, int port) {
    if (peerName != null && !peerName.equals(peerIp)) {
      span.setAttribute(SemanticAttributes.NET_PEER_NAME, peerName);
    }
    if (peerIp != null) {
      span.setAttribute(SemanticAttributes.NET_PEER_IP, peerIp);
    }

    String peerService = mapToPeerService(peerName);
    if (peerService == null) {
      peerService = mapToPeerService(peerIp);
    }
    if (peerService != null) {
      span.setAttribute(SemanticAttributes.PEER_SERVICE, peerService);
    }
    if (port > 0) {
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, (long) port);
    }
  }

  private String mapToPeerService(String endpoint) {
    if (endpoint == null) {
      return null;
    }

    return peerServiceMapping.get(endpoint);
  }

  /**
   * This helper interface allows setting attributes on both {@link Span} and {@link SpanBuilder}.
   */
  @FunctionalInterface
  public interface SpanAttributeSetter {
    <T> void setAttribute(AttributeKey<T> key, @Nullable T value);
  }
}
