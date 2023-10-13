/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InternalNetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.InternalUrlAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-spans.md#http-server">HTTP
 * server attributes</a>. Instrumentation of HTTP server frameworks should extend this class,
 * defining {@link REQUEST} and {@link RESPONSE} with the actual request / response types of the
 * instrumented library. If an attribute is not available in this library, it is appropriate to
 * return {@code null} from the protected attribute methods, but implement as many as possible for
 * best compliance with the OpenTelemetry specification.
 */
public final class HttpServerAttributesExtractor<REQUEST, RESPONSE>
    extends HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, HttpServerAttributesGetter<REQUEST, RESPONSE>>
    implements SpanKeyProvider {

  /** Creates the HTTP server attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    return builder(httpAttributesGetter).build();
  }

  /**
   * Creates the HTTP server attributes extractor with default configuration.
   *
   * @deprecated Make sure that your {@linkplain HttpServerAttributesGetter getter} implements all
   *     the network-related methods and use {@link #create(HttpServerAttributesGetter)} instead.
   *     This method will be removed in the 2.0 release.
   */
  @Deprecated
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter<
              REQUEST, RESPONSE>
          netAttributesGetter) {
    return builder(httpAttributesGetter, netAttributesGetter).build();
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractorBuilder} that can be used to configure the
   * HTTP client attributes extractor.
   */
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    return new HttpServerAttributesExtractorBuilder<>(httpAttributesGetter, httpAttributesGetter);
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractorBuilder} that can be used to configure the
   * HTTP client attributes extractor.
   *
   * @deprecated Make sure that your {@linkplain HttpServerAttributesGetter getter} implements all
   *     the network-related methods and use {@link #builder(HttpServerAttributesGetter)} instead.
   *     This method will be removed in the 2.0 release.
   */
  @Deprecated
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter<
              REQUEST, RESPONSE>
          netAttributesGetter) {
    return new HttpServerAttributesExtractorBuilder<>(httpAttributesGetter, netAttributesGetter);
  }

  private final InternalUrlAttributesExtractor<REQUEST> internalUrlExtractor;
  private final InternalNetServerAttributesExtractor<REQUEST, RESPONSE> internalNetExtractor;
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final InternalServerAttributesExtractor<REQUEST, RESPONSE> internalServerExtractor;
  private final InternalClientAttributesExtractor<REQUEST, RESPONSE> internalClientExtractor;
  private final Function<Context, String> httpRouteGetter;

  HttpServerAttributesExtractor(HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> builder) {
    super(
        builder.httpAttributesGetter,
        HttpStatusCodeConverter.SERVER,
        builder.capturedRequestHeaders,
        builder.capturedResponseHeaders,
        builder.knownMethods);
    internalUrlExtractor = builder.buildUrlExtractor();
    internalNetExtractor = builder.buildNetExtractor();
    internalNetworkExtractor = builder.buildNetworkExtractor();
    internalServerExtractor = builder.buildServerExtractor();
    internalClientExtractor = builder.buildClientExtractor();
    httpRouteGetter = builder.httpRouteGetter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);

    internalUrlExtractor.onStart(attributes, request);
    internalNetExtractor.onStart(attributes, request);
    internalServerExtractor.onStart(attributes, request);
    internalClientExtractor.onStart(attributes, request);

    internalSet(attributes, SemanticAttributes.HTTP_ROUTE, getter.getHttpRoute(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    super.onEnd(attributes, context, request, response, error);

    internalNetworkExtractor.onEnd(attributes, request, response);
    internalServerExtractor.onEnd(attributes, request, response);
    internalClientExtractor.onEnd(attributes, request, response);

    internalSet(attributes, SemanticAttributes.HTTP_ROUTE, httpRouteGetter.apply(context));
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.HTTP_SERVER;
  }
}
