/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetServerAttributesExtractor<REQUEST> {

  private static final Logger logger =
      Logger.getLogger(InternalNetServerAttributesExtractor.class.getName());

  private final NetServerAttributesGetter<REQUEST> getter;
  private final BiPredicate<Integer, REQUEST> captureHostPortCondition;

  public InternalNetServerAttributesExtractor(
      NetServerAttributesGetter<REQUEST> getter,
      BiPredicate<Integer, REQUEST> captureHostPortCondition) {
    this.getter = getter;
    this.captureHostPortCondition = captureHostPortCondition;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request, @Nullable String hostHeader) {
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

    String hostName = getter.hostName(request);
    Integer hostPort = getter.hostPort(request);

    int hostHeaderSeparator = -1;
    if (hostHeader != null) {
      hostHeaderSeparator = hostHeader.indexOf(':');
    }
    if (hostName == null && hostHeader != null) {
      hostName =
          hostHeaderSeparator == -1 ? hostHeader : hostHeader.substring(0, hostHeaderSeparator);
    }
    if (hostPort == null && hostHeader != null && hostHeaderSeparator != -1) {
      try {
        hostPort = Integer.parseInt(hostHeader.substring(hostHeaderSeparator + 1));
      } catch (NumberFormatException e) {
        logger.log(FINE, e.getMessage(), e);
      }
    }

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
}
