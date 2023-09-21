/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.network.ClientAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalClientAttributesExtractor<REQUEST, RESPONSE> {

  private final ClientAttributesGetter<REQUEST, RESPONSE> getter;
  private final FallbackAddressPortExtractor<REQUEST> fallbackAddressPortExtractor;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;

  public InternalClientAttributesExtractor(
      ClientAttributesGetter<REQUEST, RESPONSE> getter,
      FallbackAddressPortExtractor<REQUEST> fallbackAddressPortExtractor,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.fallbackAddressPortExtractor = fallbackAddressPortExtractor;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public void onStart(AttributesBuilder attributes, REQUEST request) {
    AddressAndPort clientAddressAndPort = extractClientAddressAndPort(request);

    if (emitStableUrlAttributes) {
      internalSet(attributes, SemanticAttributes.CLIENT_ADDRESS, clientAddressAndPort.address);
      if (clientAddressAndPort.port != null && clientAddressAndPort.port > 0) {
        internalSet(attributes, SemanticAttributes.CLIENT_PORT, (long) clientAddressAndPort.port);
      }
    }
    if (emitOldHttpAttributes) {
      internalSet(attributes, SemanticAttributes.HTTP_CLIENT_IP, clientAddressAndPort.address);
    }
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {
    AddressAndPort clientAddressAndPort = extractClientAddressAndPort(request);
    String clientSocketAddress = getter.getClientSocketAddress(request, response);
    Integer clientSocketPort = getter.getClientSocketPort(request, response);

    if (clientSocketAddress != null && !clientSocketAddress.equals(clientAddressAndPort.address)) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, SemanticAttributes.CLIENT_SOCKET_ADDRESS, clientSocketAddress);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_ADDR, clientSocketAddress);
      }
    }
    if (clientSocketPort != null && clientSocketPort > 0) {
      if (emitStableUrlAttributes) {
        if (!clientSocketPort.equals(clientAddressAndPort.port)) {
          internalSet(attributes, SemanticAttributes.CLIENT_SOCKET_PORT, (long) clientSocketPort);
        }
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_PORT, (long) clientSocketPort);
      }
    }
  }

  private AddressAndPort extractClientAddressAndPort(REQUEST request) {
    AddressAndPort addressAndPort = new AddressAndPort();
    addressAndPort.address = getter.getClientAddress(request);
    addressAndPort.port = getter.getClientPort(request);
    if (addressAndPort.address == null && addressAndPort.port == null) {
      fallbackAddressPortExtractor.extract(addressAndPort, request);
    }
    return addressAndPort;
  }
}
