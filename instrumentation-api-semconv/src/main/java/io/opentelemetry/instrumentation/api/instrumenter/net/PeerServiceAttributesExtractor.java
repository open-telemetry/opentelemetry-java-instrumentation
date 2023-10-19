/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class PeerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final ServerAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private final PeerServiceResolver peerServiceResolver;

  // visible for tests
  PeerServiceAttributesExtractor(
      ServerAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      PeerServiceResolver peerServiceResolver) {
    this.attributesGetter = attributesGetter;
    this.peerServiceResolver = peerServiceResolver;
  }

  /**
   * Returns a new {@link PeerServiceAttributesExtractor} that will use the passed {@code
   * attributesGetter} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      PeerServiceResolver peerServiceResolver) {
    return new PeerServiceAttributesExtractor<>(attributesGetter, peerServiceResolver);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

  @SuppressWarnings("deprecation") // old semconv
  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    if (peerServiceResolver.isEmpty()) {
      // optimization for common case
      return;
    }

    String serverAddress = attributesGetter.getServerAddress(request);
    Integer serverPort = attributesGetter.getServerPort(request);
    String peerService = mapToPeerService(serverAddress, serverPort);
    if (peerService == null && SemconvStability.emitOldHttpSemconv()) {
      String serverSocketDomain = attributesGetter.getServerSocketDomain(request, response);
      Integer serverSocketPort = attributesGetter.getServerSocketPort(request, response);
      peerService = mapToPeerService(serverSocketDomain, serverSocketPort);
    }
    if (peerService != null) {
      attributes.put(SemanticAttributes.PEER_SERVICE, peerService);
    }
  }

  @Nullable
  private String mapToPeerService(@Nullable String host, @Nullable Integer port) {
    if (host == null) {
      return null;
    }
    return peerServiceResolver.resolveService(host, port, null);
  }
}
