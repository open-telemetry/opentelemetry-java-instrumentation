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

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("deprecation") // this class will be removed in the 2.0 version
public final class InternalNetServerAttributesExtractor<REQUEST, RESPONSE> {

  private final io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter<
          REQUEST, RESPONSE>
      getter;
  private final FallbackAddressPortExtractor<REQUEST> fallbackAddressPortExtractor;
  private final boolean emitOldHttpAttributes;

  public InternalNetServerAttributesExtractor(
      io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter<
              REQUEST, RESPONSE>
          getter,
      FallbackAddressPortExtractor<REQUEST> fallbackAddressPortExtractor,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.fallbackAddressPortExtractor = fallbackAddressPortExtractor;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {

    if (emitOldHttpAttributes) {
      internalSet(attributes, SemanticAttributes.NET_TRANSPORT, getter.getTransport(request));

      boolean setSockFamily = false;

      String clientSocketAddress = getter.getClientSocketAddress(request, null);
      if (clientSocketAddress != null) {
        setSockFamily = true;
      }

      String serverSocketAddress = getter.getServerSocketAddress(request, null);
      if (serverSocketAddress != null
          && !serverSocketAddress.equals(extractServerAddress(request))) {
        setSockFamily = true;
      }

      if (setSockFamily) {
        String sockFamily = getter.getSockFamily(request);
        if (sockFamily != null && !SemanticAttributes.NetSockFamilyValues.INET.equals(sockFamily)) {
          internalSet(attributes, SemanticAttributes.NET_SOCK_FAMILY, sockFamily);
        }
      }
    }
  }

  private String extractServerAddress(REQUEST request) {
    String serverAddress = getter.getServerAddress(request);
    if (serverAddress != null) {
      return serverAddress;
    }
    AddressAndPort addressAndPort = new AddressAndPort();
    fallbackAddressPortExtractor.extract(addressAndPort, request);
    return addressAndPort.getAddress();
  }
}
