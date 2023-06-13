/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetServerAttributesExtractor<REQUEST, RESPONSE> {

  private final NetServerAttributesGetter<REQUEST, RESPONSE> getter;
  private final FallbackNamePortGetter<REQUEST> fallbackNamePortGetter;
  private final boolean emitOldHttpAttributes;

  public InternalNetServerAttributesExtractor(
      NetServerAttributesGetter<REQUEST, RESPONSE> getter,
      FallbackNamePortGetter<REQUEST> fallbackNamePortGetter,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.fallbackNamePortGetter = fallbackNamePortGetter;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {

    if (emitOldHttpAttributes) {
      internalSet(attributes, SemanticAttributes.NET_TRANSPORT, getter.getTransport(request));
    }

    boolean setSockFamily = false;

    String sockPeerAddr = getter.getSockPeerAddr(request);
    if (sockPeerAddr != null) {
      setSockFamily = true;

      internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_ADDR, sockPeerAddr);

      Integer sockPeerPort = getter.getSockPeerPort(request);
      if (sockPeerPort != null && sockPeerPort > 0) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_PORT, (long) sockPeerPort);
      }
    }

    String hostName = extractHostName(request);
    String sockHostAddr = getter.getServerSocketAddress(request, null);
    if (sockHostAddr != null && !sockHostAddr.equals(hostName)) {
      setSockFamily = true;
    }

    if (emitOldHttpAttributes && setSockFamily) {
      String sockFamily = getter.getSockFamily(request);
      if (sockFamily != null && !SemanticAttributes.NetSockFamilyValues.INET.equals(sockFamily)) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_FAMILY, sockFamily);
      }
    }
  }

  private String extractHostName(REQUEST request) {
    String peerName = getter.getServerAddress(request);
    if (peerName == null) {
      peerName = fallbackNamePortGetter.name(request);
    }
    return peerName;
  }
}
