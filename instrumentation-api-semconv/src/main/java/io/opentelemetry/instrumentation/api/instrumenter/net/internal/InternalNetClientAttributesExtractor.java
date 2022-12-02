/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetClientAttributesExtractor<REQUEST, RESPONSE> {

  private final NetClientAttributesGetter<REQUEST, RESPONSE> getter;
  private final BiPredicate<Integer, REQUEST> capturePeerPortCondition;
  private final FallbackNamePortGetter<REQUEST> fallbackNamePortGetter;

  public InternalNetClientAttributesExtractor(
      NetClientAttributesGetter<REQUEST, RESPONSE> getter,
      BiPredicate<Integer, REQUEST> capturePeerPortCondition,
      FallbackNamePortGetter<REQUEST> fallbackNamePortGetter) {
    this.getter = getter;
    this.capturePeerPortCondition = capturePeerPortCondition;
    this.fallbackNamePortGetter = fallbackNamePortGetter;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    String peerName = extractPeerName(request);

    if (peerName != null) {
      internalSet(attributes, SemanticAttributes.NET_PEER_NAME, peerName);

      Integer peerPort = extractPeerPort(request);
      if (peerPort != null && peerPort > 0 && capturePeerPortCondition.test(peerPort, request)) {
        internalSet(attributes, SemanticAttributes.NET_PEER_PORT, (long) peerPort);
      }
    }
  }

  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {

    internalSet(attributes, SemanticAttributes.NET_TRANSPORT, getter.transport(request, response));

    String peerName = extractPeerName(request);

    String sockPeerAddr = getter.sockPeerAddr(request, response);
    if (sockPeerAddr != null && !sockPeerAddr.equals(peerName)) {
      internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_ADDR, sockPeerAddr);

      Integer peerPort = extractPeerPort(request);
      Integer sockPeerPort = getter.sockPeerPort(request, response);
      if (sockPeerPort != null && sockPeerPort > 0 && !sockPeerPort.equals(peerPort)) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_PORT, (long) sockPeerPort);
      }

      String sockFamily = getter.sockFamily(request, response);
      if (sockFamily != null && !SemanticAttributes.NetSockFamilyValues.INET.equals(sockFamily)) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_FAMILY, sockFamily);
      }

      String sockPeerName = getter.sockPeerName(request, response);
      if (sockPeerName != null && !sockPeerName.equals(peerName)) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_NAME, sockPeerName);
      }
    }
  }

  private String extractPeerName(REQUEST request) {
    String peerName = getter.peerName(request);
    if (peerName == null) {
      peerName = fallbackNamePortGetter.name(request);
    }
    return peerName;
  }

  private Integer extractPeerPort(REQUEST request) {
    Integer peerPort = getter.peerPort(request);
    if (peerPort == null) {
      peerPort = fallbackNamePortGetter.port(request);
    }
    return peerPort;
  }
}
