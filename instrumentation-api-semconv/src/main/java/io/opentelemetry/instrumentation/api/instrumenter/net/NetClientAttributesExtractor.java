/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

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

    set(attributes, SemanticAttributes.NET_TRANSPORT, getter.transport(request, response));

    String peerIp = getter.peerIp(request, response);
    String peerName = getter.peerName(request, response);

    if (peerName != null && !peerName.equals(peerIp)) {
      set(attributes, SemanticAttributes.NET_PEER_NAME, peerName);
    }
    set(attributes, SemanticAttributes.NET_PEER_IP, peerIp);

    Integer peerPort = getter.peerPort(request, response);
    if (peerPort != null && peerPort > 0) {
      set(attributes, SemanticAttributes.NET_PEER_PORT, (long) peerPort);
    }
  }
}
