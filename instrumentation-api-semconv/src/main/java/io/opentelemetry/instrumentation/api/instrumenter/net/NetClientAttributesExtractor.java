/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-network-connection-attributes">Network
 * attributes</a>. It is common to have access to {@link java.net.InetSocketAddress}, in which case
 * it is more convenient to use {@link InetSocketAddressNetClientAttributesGetter}.
 *
 * <p>This class delegates to a type-specific {@link NetClientAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class NetClientAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private static final AttributeKey<String> NET_SOCK_PEER_ADDR =
      AttributeKey.stringKey("net.sock.peer.addr");
  public static final AttributeKey<Long> NET_SOCK_PEER_PORT =
      AttributeKey.longKey("net.sock.peer.port");
  public static final AttributeKey<String> NET_SOCK_FAMILY =
      AttributeKey.stringKey("net.sock.family");
  public static final AttributeKey<String> NET_SOCK_PEER_NAME =
      AttributeKey.stringKey("net.sock.peer.name");

  private final NetClientAttributesGetter<REQUEST, RESPONSE> getter;

  public static <REQUEST, RESPONSE> NetClientAttributesExtractor<REQUEST, RESPONSE> create(
      NetClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new NetClientAttributesExtractor<>(getter);
  }

  private NetClientAttributesExtractor(NetClientAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    internalSet(attributes, SemanticAttributes.NET_TRANSPORT, getter.transport(request, response));

    String peerName = getter.peerName(request, response);
    Integer peerPort = getter.peerPort(request, response);
    if (peerName != null) {
      internalSet(attributes, SemanticAttributes.NET_PEER_NAME, peerName);
      if (peerPort != null && peerPort > 0) {
        internalSet(attributes, SemanticAttributes.NET_PEER_PORT, (long) peerPort);
      }
    }

    String sockPeerAddr = getter.sockPeerAddr(request, response);
    if (sockPeerAddr != null && !sockPeerAddr.equals(peerName)) {
      internalSet(attributes, NET_SOCK_PEER_ADDR, sockPeerAddr);

      Integer sockPeerPort = getter.sockPeerPort(request, response);
      if (sockPeerPort != null && sockPeerPort > 0 && !sockPeerPort.equals(peerPort)) {
        internalSet(attributes, NET_SOCK_PEER_PORT, (long) sockPeerPort);
      }

      internalSet(attributes, NET_SOCK_FAMILY, getter.sockFamily(request, response));

      String sockPeerName = getter.sockPeerName(request, response);
      if (sockPeerName != null && !sockPeerName.equals(peerName)) {
        internalSet(attributes, NET_SOCK_PEER_NAME, sockPeerName);
      }
    }
  }
}
