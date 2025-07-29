/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.semconv.NetworkAttributes;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetworkAttributesExtractor<REQUEST, RESPONSE> {

  private final NetworkAttributesGetter<REQUEST, RESPONSE> getter;
  private final boolean captureProtocolAttributes;
  private final boolean captureLocalSocketAttributes;

  public InternalNetworkAttributesExtractor(
      NetworkAttributesGetter<REQUEST, RESPONSE> getter,
      boolean captureProtocolAttributes,
      boolean captureLocalSocketAttributes) {
    this.getter = getter;
    this.captureProtocolAttributes = captureProtocolAttributes;
    this.captureLocalSocketAttributes = captureLocalSocketAttributes;
  }

  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {
    if (captureProtocolAttributes) {
      internalSet(
          attributes,
          NetworkAttributes.NETWORK_TRANSPORT,
          lowercase(getter.getNetworkTransport(request, response)));
      internalSet(
          attributes,
          NetworkAttributes.NETWORK_TYPE,
          lowercase(getter.getNetworkType(request, response)));
      internalSet(
          attributes,
          NetworkAttributes.NETWORK_PROTOCOL_NAME,
          lowercase(getter.getNetworkProtocolName(request, response)));
      internalSet(
          attributes,
          NetworkAttributes.NETWORK_PROTOCOL_VERSION,
          lowercase(getter.getNetworkProtocolVersion(request, response)));
    }

    if (captureLocalSocketAttributes) {
      String localAddress = getter.getNetworkLocalAddress(request, response);
      if (localAddress != null) {
        internalSet(attributes, NetworkAttributes.NETWORK_LOCAL_ADDRESS, localAddress);

        Integer localPort = getter.getNetworkLocalPort(request, response);
        if (localPort != null && localPort > 0) {
          internalSet(attributes, NetworkAttributes.NETWORK_LOCAL_PORT, (long) localPort);
        }
      }
    }

    String peerAddress = getter.getNetworkPeerAddress(request, response);
    if (peerAddress != null) {
      internalSet(attributes, NetworkAttributes.NETWORK_PEER_ADDRESS, peerAddress);

      Integer peerPort = getter.getNetworkPeerPort(request, response);
      if (peerPort != null && peerPort > 0) {
        internalSet(attributes, NetworkAttributes.NETWORK_PEER_PORT, (long) peerPort);
      }
    }
  }

  @Nullable
  private static String lowercase(@Nullable String str) {
    return str == null ? null : str.toLowerCase(Locale.ROOT);
  }
}
