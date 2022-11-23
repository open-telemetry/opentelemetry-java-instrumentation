/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.function.BiPredicate;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetServerAttributesExtractor<REQUEST> {

  private final NetServerAttributesGetter<REQUEST> getter;
  private final BiPredicate<Integer, REQUEST> captureHostPortCondition;
  private final AlternativeNamePortGetter<REQUEST> alternativeNamePortGetter;

  public InternalNetServerAttributesExtractor(
      NetServerAttributesGetter<REQUEST> getter,
      BiPredicate<Integer, REQUEST> captureHostPortCondition,
      AlternativeNamePortGetter<REQUEST> alternativeNamePortGetter) {
    this.getter = getter;
    this.captureHostPortCondition = captureHostPortCondition;
    this.alternativeNamePortGetter = alternativeNamePortGetter;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    internalSet(attributes, SemanticAttributes.NET_TRANSPORT, getter.transport(request));

    boolean setSockFamily = false;

    String sockPeerAddr = getter.sockPeerAddr(request);
    if (sockPeerAddr != null) {
      setSockFamily = true;

      internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_ADDR, sockPeerAddr);

      Integer sockPeerPort = getter.sockPeerPort(request);
      if (sockPeerPort != null && sockPeerPort > 0) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_PORT, (long) sockPeerPort);
      }
    }

    String hostName = extractHostName(request);
    Integer hostPort = extractHostPort(request);

    if (hostName != null) {
      internalSet(attributes, SemanticAttributes.NET_HOST_NAME, hostName);

      if (hostPort != null && hostPort > 0 && captureHostPortCondition.test(hostPort, request)) {
        internalSet(attributes, SemanticAttributes.NET_HOST_PORT, (long) hostPort);
      }
    }

    String sockHostAddr = getter.sockHostAddr(request);
    if (sockHostAddr != null && !sockHostAddr.equals(hostName)) {
      setSockFamily = true;

      internalSet(attributes, SemanticAttributes.NET_SOCK_HOST_ADDR, sockHostAddr);

      Integer sockHostPort = getter.sockHostPort(request);
      if (sockHostPort != null && sockHostPort > 0 && !sockHostPort.equals(hostPort)) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_HOST_PORT, (long) sockHostPort);
      }
    }

    if (setSockFamily) {
      String sockFamily = getter.sockFamily(request);
      if (sockFamily != null && !SemanticAttributes.NetSockFamilyValues.INET.equals(sockFamily)) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_FAMILY, sockFamily);
      }
    }
  }

  private String extractHostName(REQUEST request) {
    String peerName = getter.hostName(request);
    if (peerName == null) {
      peerName = alternativeNamePortGetter.name(request);
    }
    return peerName;
  }

  private Integer extractHostPort(REQUEST request) {
    Integer peerPort = getter.hostPort(request);
    if (peerPort == null) {
      peerPort = alternativeNamePortGetter.port(request);
    }
    return peerPort;
  }
}
