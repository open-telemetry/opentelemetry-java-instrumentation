/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InetSocketAddressUtil;
import io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetworkAttributesExtractor<REQUEST, RESPONSE> {

  private final NetworkAttributesGetter<REQUEST, RESPONSE> getter;
  private final AddressAndPortExtractor<REQUEST> logicalLocalAddressAndPortExtractor;
  private final AddressAndPortExtractor<REQUEST> logicalPeerAddressAndPortExtractor;
  private final boolean captureNetworkTransportAndType;
  private final boolean captureLocalSocketAttributes;
  private final boolean captureOldPeerDomainAttribute;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;

  public InternalNetworkAttributesExtractor(
      NetworkAttributesGetter<REQUEST, RESPONSE> getter,
      AddressAndPortExtractor<REQUEST> logicalLocalAddressAndPortExtractor,
      AddressAndPortExtractor<REQUEST> logicalPeerAddressAndPortExtractor,
      boolean captureNetworkTransportAndType,
      boolean captureLocalSocketAttributes,
      boolean captureOldPeerDomainAttribute,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.logicalLocalAddressAndPortExtractor = logicalLocalAddressAndPortExtractor;
    this.logicalPeerAddressAndPortExtractor = logicalPeerAddressAndPortExtractor;
    this.captureNetworkTransportAndType = captureNetworkTransportAndType;
    this.captureLocalSocketAttributes = captureLocalSocketAttributes;
    this.captureOldPeerDomainAttribute = captureOldPeerDomainAttribute;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {
    String protocolName = lowercase(getter.getNetworkProtocolName(request, response));
    String protocolVersion = lowercase(getter.getNetworkProtocolVersion(request, response));

    if (emitStableUrlAttributes) {
      String transport = lowercase(getter.getNetworkTransport(request, response));
      if (captureNetworkTransportAndType) {
        internalSet(attributes, SemanticAttributes.NETWORK_TRANSPORT, transport);
        internalSet(
            attributes,
            SemanticAttributes.NETWORK_TYPE,
            lowercase(getter.getNetworkType(request, response)));
      }
      internalSet(attributes, SemanticAttributes.NETWORK_PROTOCOL_NAME, protocolName);
      internalSet(attributes, SemanticAttributes.NETWORK_PROTOCOL_VERSION, protocolVersion);
    }
    if (emitOldHttpAttributes) {
      // net.transport and net.sock.family are not 1:1 convertible with network.transport and
      // network.type; they must be handled separately in the old net.* extractors
      internalSet(attributes, SemanticAttributes.NET_PROTOCOL_NAME, protocolName);
      internalSet(attributes, SemanticAttributes.NET_PROTOCOL_VERSION, protocolVersion);
    }

    String localAddress = getter.getNetworkLocalAddress(request, response);
    String logicalLocalAddress = logicalLocalAddressAndPortExtractor.extract(request).address;
    if (localAddress != null && !localAddress.equals(logicalLocalAddress)) {
      if (emitStableUrlAttributes && captureLocalSocketAttributes) {
        internalSet(attributes, NetworkAttributes.NETWORK_LOCAL_ADDRESS, localAddress);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_HOST_ADDR, localAddress);
      }

      Integer localPort = getter.getNetworkLocalPort(request, response);
      if (localPort != null && localPort > 0) {
        if (emitStableUrlAttributes && captureLocalSocketAttributes) {
          internalSet(attributes, NetworkAttributes.NETWORK_LOCAL_PORT, (long) localPort);
        }
        if (emitOldHttpAttributes) {
          internalSet(attributes, SemanticAttributes.NET_SOCK_HOST_PORT, (long) localPort);
        }
      }
    }

    String peerAddress = getter.getNetworkPeerAddress(request, response);
    String logicalPeerAddress = logicalPeerAddressAndPortExtractor.extract(request).address;
    if (peerAddress != null && !peerAddress.equals(logicalPeerAddress)) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, NetworkAttributes.NETWORK_PEER_ADDRESS, peerAddress);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_ADDR, peerAddress);
      }

      Integer peerPort = getter.getNetworkPeerPort(request, response);
      if (peerPort != null && peerPort > 0) {
        if (emitStableUrlAttributes) {
          internalSet(attributes, NetworkAttributes.NETWORK_PEER_PORT, (long) peerPort);
        }
        if (emitOldHttpAttributes) {
          internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_PORT, (long) peerPort);
        }
      }

      if (emitOldHttpAttributes && captureOldPeerDomainAttribute) {
        InetSocketAddress peerSocketAddress =
            getter.getNetworkPeerInetSocketAddress(request, response);
        if (peerSocketAddress != null) {
          String peerSocketDomain = InetSocketAddressUtil.getDomainName(peerSocketAddress);
          if (peerSocketDomain != null && !peerSocketDomain.equals(logicalPeerAddress)) {
            internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_NAME, peerSocketDomain);
          }
        }
      }
    }
  }

  @Nullable
  private static String lowercase(@Nullable String str) {
    return str == null ? null : str.toLowerCase(Locale.ROOT);
  }
}
