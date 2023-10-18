/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.ServerAddressAndPortExtractor;
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
  private final AddressAndPortExtractor<REQUEST> logicalAddressAndPortExtractor;
  private final boolean emitOldHttpAttributes;

  public InternalNetClientAttributesExtractor(
      io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
              REQUEST, RESPONSE>
          getter,
      AddressAndPortExtractor<REQUEST> fallbackAddressAndPortExtractor,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.logicalAddressAndPortExtractor =
        new ServerAddressAndPortExtractor<>(getter, fallbackAddressAndPortExtractor);
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {

    if (emitOldHttpAttributes) {
      internalSet(
          attributes, SemanticAttributes.NET_TRANSPORT, getter.getTransport(request, response));

      String peerName = logicalAddressAndPortExtractor.extract(request).getAddress();
      String sockPeerAddr = getter.getServerSocketAddress(request, response);
      if (sockPeerAddr != null && !sockPeerAddr.equals(peerName)) {
        String sockFamily = getter.getSockFamily(request, response);
        if (sockFamily != null && !SemanticAttributes.NetSockFamilyValues.INET.equals(sockFamily)) {
          internalSet(attributes, SemanticAttributes.NET_SOCK_FAMILY, sockFamily);
        }
      }
    }
  }
}
