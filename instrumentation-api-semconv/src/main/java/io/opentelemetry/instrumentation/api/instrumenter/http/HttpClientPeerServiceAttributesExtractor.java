/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class HttpClientPeerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private final PeerServiceResolver peerServiceResolver;

  // visible for tests
  HttpClientPeerServiceAttributesExtractor(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      PeerServiceResolver peerServiceResolver) {
    this.attributesGetter = attributesGetter;
    this.peerServiceResolver = peerServiceResolver;
  }

  /**
   * Returns a new {@link HttpClientPeerServiceAttributesExtractor} that will use the passed {@code
   * attributesGetter} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE>
      HttpClientPeerServiceAttributesExtractor<REQUEST, RESPONSE> create(
          HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
          PeerServiceResolver peerServiceResolver) {
    return new HttpClientPeerServiceAttributesExtractor<>(attributesGetter, peerServiceResolver);
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
    Supplier<String> pathSupplier = () -> getUrlPath(attributesGetter, request);
    String peerService = mapToPeerService(serverAddress, serverPort, pathSupplier);
    if (peerService == null && SemconvStability.emitOldHttpSemconv()) {
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
      @Nullable String host, @Nullable Integer port, @Nullable Supplier<String> pathSupplier) {
    if (host == null) {
      return null;
    }
    return peerServiceResolver.resolveService(host, port, pathSupplier);
  }

  @Nullable
  private String getUrlPath(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter, REQUEST request) {
    String urlFull = attributesGetter.getUrlFull(request);
    if (urlFull == null) {
      return null;
    }
    return UrlParser.getPath(urlFull);
  }
}
