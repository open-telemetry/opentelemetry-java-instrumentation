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
 * it is more convenient to use {@link InetSocketAddressNetServerAttributesGetter}.
 */
public final class NetServerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  public static final AttributeKey<String> NET_SOCK_PEER_ADDR =
      AttributeKey.stringKey("net.sock.peer.addr");
  public static final AttributeKey<Long> NET_SOCK_PEER_PORT =
      AttributeKey.longKey("net.sock.peer.port");
  private final NetServerAttributesGetter<REQUEST> getter;

  public static <REQUEST, RESPONSE> NetServerAttributesExtractor<REQUEST, RESPONSE> create(
      NetServerAttributesGetter<REQUEST> getter) {
    return new NetServerAttributesExtractor<>(getter);
  }

  private NetServerAttributesExtractor(NetServerAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, SemanticAttributes.NET_TRANSPORT, getter.transport(request));

    String sockPeerAddr = getter.sockPeerAddr(request);

    internalSet(attributes, NET_SOCK_PEER_ADDR, sockPeerAddr);

    Integer sockPeerPort = getter.sockPeerPort(request);
    if (sockPeerPort != null && sockPeerPort > 0) {
      internalSet(attributes, NET_SOCK_PEER_PORT, (long) sockPeerPort);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
