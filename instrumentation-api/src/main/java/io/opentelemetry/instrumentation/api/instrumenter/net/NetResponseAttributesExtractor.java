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
 * it is more convenient to use {@link InetSocketAddressNetResponseAttributesExtractor}.
 */
public abstract class NetResponseAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {

  @Override
  protected final void onStart(AttributesBuilder attributes, REQUEST request) {}

  @Override
  protected final void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    set(attributes, SemanticAttributes.NET_TRANSPORT, transport(response));

    String peerIp = peerIp(response);
    String peerName = peerName(response);

    if (peerName != null && !peerName.equals(peerIp)) {
      set(attributes, SemanticAttributes.NET_PEER_NAME, peerName);
    }
    set(attributes, SemanticAttributes.NET_PEER_IP, peerIp);

    Integer peerPort = peerPort(response);
    if (peerPort != null) {
      set(attributes, SemanticAttributes.NET_PEER_PORT, (long) peerPort);
    }
  }

  @Nullable
  public abstract String transport(@Nullable RESPONSE response);

  /**
   * This method will be called twice: both when the request starts ({@code response} is always null
   * then) and when the response ends. This way it is possible to capture net attributes in both
   * phases of processing.
   */
  @Nullable
  public abstract String peerName(@Nullable RESPONSE response);

  /**
   * This method will be called twice: both when the request starts ({@code response} is always null
   * then) and when the response ends. This way it is possible to capture net attributes in both
   * phases of processing.
   */
  @Nullable
  public abstract Integer peerPort(@Nullable RESPONSE response);

  /**
   * This method will be called twice: both when the request starts ({@code response} is always null
   * then) and when the response ends. This way it is possible to capture net attributes in both
   * phases of processing.
   */
  @Nullable
  public abstract String peerIp(@Nullable RESPONSE response);
}
