/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class PeerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private static final Logger logger =
      Logger.getLogger(PeerServiceAttributesExtractor.class.getName());
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
   * netAttributesExtractor} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      PeerServiceResolver peerServiceResolver) {
    return new PeerServiceAttributesExtractor<>(attributesGetter, peerServiceResolver);
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

    if (peerServiceResolver.isEmpty()) {
      // optimization for common case
      return;
    }

    String path = null;
    String serverAddress = attributesGetter.getServerAddress(request);
    Integer serverPort = attributesGetter.getServerPort(request);
    if (attributesGetter instanceof HttpClientAttributesGetter<?, ?>) {
      String urlFull =
          ((HttpClientAttributesGetter<REQUEST, RESPONSE>) attributesGetter).getUrlFull(request);
      if (urlFull != null) {
        try {
          URI uri = new URI(urlFull);
          path = uri.getPath();
        } catch (URISyntaxException use) {
          logger.warning("Failed to parse URI from " + urlFull + " with : " + use.getMessage());
        }
      }
    }
    String peerService = mapToPeerService(serverAddress, serverPort, path);
    if (peerService == null) {
      String serverSocketDomain = attributesGetter.getServerSocketDomain(request, response);
      Integer serverSocketPort = attributesGetter.getServerSocketPort(request, response);
      peerService = mapToPeerService(serverSocketDomain, serverSocketPort, null);
    }
    if (peerService != null) {
      attributes.put(SemanticAttributes.PEER_SERVICE, peerService);
    }
  }

  @Nullable
  private String mapToPeerService(
      @Nullable String host, @Nullable Integer port, @Nullable String path) {
    if (host == null) {
      return null;
    }
    return peerServiceResolver.resolveService(host, port, path);
  }
}
