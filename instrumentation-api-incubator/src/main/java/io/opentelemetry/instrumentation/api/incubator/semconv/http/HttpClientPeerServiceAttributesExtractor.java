/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HostAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPort;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.ServerAddressAndPortExtractor;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/general/attributes.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class HttpClientPeerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from PeerIncubatingAttributes
  private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");

  private final AddressAndPortExtractor<REQUEST> addressAndPortExtractor;
  private final HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private final PeerServiceResolver peerServiceResolver;

  // visible for tests
  HttpClientPeerServiceAttributesExtractor(
      AddressAndPortExtractor<REQUEST> addressAndPortExtractor,
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      PeerServiceResolver peerServiceResolver) {
    this.addressAndPortExtractor = addressAndPortExtractor;
    this.attributesGetter = attributesGetter;
    this.peerServiceResolver = peerServiceResolver;
  }

  /**
   * Returns a new {@link HttpClientPeerServiceAttributesExtractor} that will use the passed {@code
   * attributesGetter} to extract server address and port (with fallback to the HTTP Host header).
   */
  public static <REQUEST, RESPONSE>
      HttpClientPeerServiceAttributesExtractor<REQUEST, RESPONSE> create(
          HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter,
          PeerServiceResolver peerServiceResolver) {
    AddressAndPortExtractor<REQUEST> addressAndPortExtractor =
        new ServerAddressAndPortExtractor<>(
            attributesGetter, new HostAddressAndPortExtractor<>(attributesGetter));
    return new HttpClientPeerServiceAttributesExtractor<>(
        addressAndPortExtractor, attributesGetter, peerServiceResolver);
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

    AddressAndPort addressAndPort = addressAndPortExtractor.extract(request);

    Supplier<String> pathSupplier = () -> getUrlPath(attributesGetter, request);
    String peerService =
        mapToPeerService(addressAndPort.getAddress(), addressAndPort.getPort(), pathSupplier);
    if (peerService != null) {
      attributes.put(PEER_SERVICE, peerService);
    }
  }

  @Nullable
  private String mapToPeerService(
      @Nullable String host, @Nullable Integer port, Supplier<String> pathSupplier) {
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
