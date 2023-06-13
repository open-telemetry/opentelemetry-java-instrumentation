/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetClientAttributesExtractor<REQUEST, RESPONSE> {

  private final NetClientAttributesGetter<REQUEST, RESPONSE> getter;
  private final FallbackNamePortGetter<REQUEST> fallbackNamePortGetter;
  private final boolean emitOldHttpAttributes;

  public InternalNetClientAttributesExtractor(
      NetClientAttributesGetter<REQUEST, RESPONSE> getter,
      FallbackNamePortGetter<REQUEST> fallbackNamePortGetter,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.fallbackNamePortGetter = fallbackNamePortGetter;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {

    if (emitOldHttpAttributes) {
      internalSet(
          attributes, SemanticAttributes.NET_TRANSPORT, getter.getTransport(request, response));

      String peerName = extractPeerName(request);
      String sockPeerAddr = getter.getServerSocketAddress(request, response);
      if (sockPeerAddr != null && !sockPeerAddr.equals(peerName)) {
        String sockFamily = getter.getSockFamily(request, response);
        if (sockFamily != null && !SemanticAttributes.NetSockFamilyValues.INET.equals(sockFamily)) {
          internalSet(attributes, SemanticAttributes.NET_SOCK_FAMILY, sockFamily);
        }
      }
    }
  }

  private String extractPeerName(REQUEST request) {
    String peerName = getter.getServerAddress(request);
    if (peerName == null) {
      peerName = fallbackNamePortGetter.name(request);
    }
    return peerName;
  }
}
