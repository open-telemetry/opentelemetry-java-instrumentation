/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.FallbackNamePortGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalClientAttributesExtractor<REQUEST, RESPONSE> {

  private final ClientAttributesGetter<REQUEST, RESPONSE> getter;
  private final FallbackNamePortGetter<REQUEST> fallbackNamePortGetter;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;

  public InternalClientAttributesExtractor(
      ClientAttributesGetter<REQUEST, RESPONSE> getter,
      FallbackNamePortGetter<REQUEST> fallbackNamePortGetter,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.fallbackNamePortGetter = fallbackNamePortGetter;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    String clientAddress = extractClientAddress(request);
    if (emitStableUrlAttributes) {
      internalSet(attributes, NetworkAttributes.CLIENT_ADDRESS, clientAddress);
      Integer clientPort = extractClientPort(request);
      if (clientPort != null && clientPort > 0) {
        internalSet(attributes, NetworkAttributes.CLIENT_PORT, (long) clientPort);
      }
    }
    if (emitOldHttpAttributes) {
      internalSet(attributes, SemanticAttributes.HTTP_CLIENT_IP, clientAddress);
    }
  }

  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {
    String clientAddress = extractClientAddress(request);
    String clientSocketAddress = getter.getClientSocketAddress(request, response);
    Integer clientSocketPort = getter.getClientSocketPort(request, response);

    if (clientSocketAddress != null && !clientSocketAddress.equals(clientAddress)) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, NetworkAttributes.CLIENT_SOCKET_ADDRESS, clientSocketAddress);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_ADDR, clientSocketAddress);
      }
    }
    if (clientSocketPort != null && clientSocketPort > 0) {
      if (emitStableUrlAttributes) {
        Integer clientPort = extractClientPort(request);
        if (!clientSocketPort.equals(clientPort)) {
          internalSet(attributes, NetworkAttributes.CLIENT_SOCKET_PORT, (long) clientSocketPort);
        }
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_PORT, (long) clientSocketPort);
      }
    }
  }

  private String extractClientAddress(REQUEST request) {
    String clientAddress = getter.getClientAddress(request);
    if (clientAddress == null) {
      clientAddress = fallbackNamePortGetter.name(request);
    }
    return clientAddress;
  }

  private Integer extractClientPort(REQUEST request) {
    Integer clientPort = getter.getClientPort(request);
    if (clientPort == null) {
      clientPort = fallbackNamePortGetter.port(request);
    }
    return clientPort;
  }
}
