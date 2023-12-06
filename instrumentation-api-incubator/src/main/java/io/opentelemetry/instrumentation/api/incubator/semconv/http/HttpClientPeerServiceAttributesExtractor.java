/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributeGetter;
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

  private final HttpClientAttributeGetter<REQUEST, RESPONSE> attributeGetter;
  private final PeerServiceResolver peerServiceResolver;

  // visible for tests
  HttpClientPeerServiceAttributesExtractor(
      HttpClientAttributeGetter<REQUEST, RESPONSE> attributeGetter,
      PeerServiceResolver peerServiceResolver) {
    this.attributeGetter = attributeGetter;
    this.peerServiceResolver = peerServiceResolver;
  }

  /**
   * Returns a new {@link HttpClientPeerServiceAttributesExtractor} that will use the passed {@code
   * attributeGetter} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE>
      HttpClientPeerServiceAttributesExtractor<REQUEST, RESPONSE> create(
          HttpClientAttributeGetter<REQUEST, RESPONSE> attributeGetter,
          PeerServiceResolver peerServiceResolver) {
    return new HttpClientPeerServiceAttributesExtractor<>(attributeGetter, peerServiceResolver);
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

    String serverAddress = attributeGetter.getServerAddress(request);
    Integer serverPort = attributeGetter.getServerPort(request);
    Supplier<String> pathSupplier = () -> getUrlPath(attributeGetter, request);
    String peerService = mapToPeerService(serverAddress, serverPort, pathSupplier);
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
      HttpClientAttributeGetter<REQUEST, RESPONSE> attributeGetter, REQUEST request) {
    String urlFull = attributeGetter.getUrlFull(request);
    if (urlFull == null) {
      return null;
    }
    return UrlParser.getPath(urlFull);
  }
}
