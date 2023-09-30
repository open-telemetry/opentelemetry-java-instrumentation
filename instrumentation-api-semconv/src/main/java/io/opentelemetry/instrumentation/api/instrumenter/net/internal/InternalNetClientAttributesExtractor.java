/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.AddressAndPort;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.FallbackAddressPortExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("deprecation") // this class will be removed in the 2.0 version
public final class InternalNetClientAttributesExtractor<REQUEST, RESPONSE> {

  private final io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
          REQUEST, RESPONSE>
      getter;
  private final FallbackAddressPortExtractor<REQUEST> fallbackAddressPortExtractor;
  private final boolean emitOldHttpAttributes;

  public InternalNetClientAttributesExtractor(
      io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
              REQUEST, RESPONSE>
          getter,
      FallbackAddressPortExtractor<REQUEST> fallbackAddressPortExtractor,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.fallbackAddressPortExtractor = fallbackAddressPortExtractor;
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
    String serverAddress = getter.getServerAddress(request);
    if (serverAddress != null) {
      return serverAddress;
    }
    AddressAndPort addressAndPort = new AddressAndPort();
    fallbackAddressPortExtractor.extract(addressAndPort, request);
    return addressAndPort.getAddress();
  }
}
