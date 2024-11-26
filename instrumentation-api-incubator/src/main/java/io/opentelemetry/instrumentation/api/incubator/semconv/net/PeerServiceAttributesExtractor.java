/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class PeerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from PeerIncubatingAttributes
  private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");

  private final ServerAttributesGetter<REQUEST> attributesGetter;
  private final PeerServiceResolver peerServiceResolver;

  // visible for tests
  PeerServiceAttributesExtractor(
      ServerAttributesGetter<REQUEST> attributesGetter, PeerServiceResolver peerServiceResolver) {
    this.attributesGetter = attributesGetter;
    this.peerServiceResolver = peerServiceResolver;
  }

  /**
   * Returns a new {@link PeerServiceAttributesExtractor} that will use the passed {@code
   * attributesGetter} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST> attributesGetter, PeerServiceResolver peerServiceResolver) {
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
    if (peerService != null) {
      attributes.put(PEER_SERVICE, peerService);
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
