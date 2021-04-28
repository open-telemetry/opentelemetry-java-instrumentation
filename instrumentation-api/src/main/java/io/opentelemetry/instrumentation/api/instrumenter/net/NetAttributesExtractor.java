/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-network-connection-attributes">Network
 * attributes</a>. It is common to have access to {@link java.net.InetSocketAddress}, in which case
 * it is more convenient to use {@link InetSocketAddressNetAttributesExtractor}.
 */
public abstract class NetAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {

  @Override
  protected final void onStart(AttributesBuilder attributes, REQUEST request) {
    set(attributes, SemanticAttributes.NET_TRANSPORT, transport(request));
    set(attributes, SemanticAttributes.NET_PEER_IP, peerIp(request, null));
    set(attributes, SemanticAttributes.NET_PEER_NAME, peerName(request, null));
    set(attributes, SemanticAttributes.NET_PEER_PORT, peerPort(request, null));
  }

  @Override
  protected final void onEnd(AttributesBuilder attributes, REQUEST request, RESPONSE response) {
    set(attributes, SemanticAttributes.NET_PEER_IP, peerIp(request, response));
    set(attributes, SemanticAttributes.NET_PEER_NAME, peerName(request, response));
    set(attributes, SemanticAttributes.NET_PEER_PORT, peerPort(request, response));
  }

  @Nullable
  protected abstract String transport(REQUEST request);

  /**
   * This method will be called twice: both when the request starts ({@code response} is always null
   * then) and when the response ends. This way it is possible to capture net attributes in both
   * phases of processing.
   */
  @Nullable
  protected abstract String peerName(REQUEST request, @Nullable RESPONSE response);

  /**
   * This method will be called twice: both when the request starts ({@code response} is always null
   * then) and when the response ends. This way it is possible to capture net attributes in both
   * phases of processing.
   */
  @Nullable
  protected abstract Long peerPort(REQUEST request, @Nullable RESPONSE response);

  /**
   * This method will be called twice: both when the request starts ({@code response} is always null
   * then) and when the response ends. This way it is possible to capture net attributes in both
   * phases of processing.
   */
  @Nullable
  protected abstract String peerIp(REQUEST request, @Nullable RESPONSE response);
}
