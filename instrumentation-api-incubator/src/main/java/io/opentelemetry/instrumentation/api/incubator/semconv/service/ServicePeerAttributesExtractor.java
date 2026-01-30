/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code service.peer.name} span attribute, described in <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class ServicePeerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from PeerIncubatingAttributes
  private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");
  // copied from ServiceIncubatingAttributes
  private static final AttributeKey<String> SERVICE_PEER_NAME =
      AttributeKey.stringKey("service.peer.name");

  private final ServerAttributesGetter<REQUEST> attributesGetter;
  private final ServicePeerResolver peerServiceResolver;

  // visible for tests
  ServicePeerAttributesExtractor(
      ServerAttributesGetter<REQUEST> attributesGetter, ServicePeerResolver peerServiceResolver) {
    this.attributesGetter = attributesGetter;
    this.peerServiceResolver = peerServiceResolver;
  }

  /**
   * Returns a new {@link ServicePeerAttributesExtractor} that will use the passed {@code
   * attributesGetter} instance to determine the value of the {@code service.peer.name} attribute.
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST> attributesGetter, ServicePeerResolver peerServiceResolver) {
    return new ServicePeerAttributesExtractor<>(attributesGetter, peerServiceResolver);
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
      if (SemconvStability.emitOldServicePeerSemconv()) {
        attributes.put(PEER_SERVICE, peerService);
      }
      if (SemconvStability.emitStableServicePeerSemconv()) {
        attributes.put(SERVICE_PEER_NAME, peerService);
      }
    }
  }

  @Nullable
  private String mapToPeerService(@Nullable String host, @Nullable Integer port) {
    if (host == null) {
      return null;
    }
    return peerServiceResolver.resolveService(host, port, () -> null);
  }
}
