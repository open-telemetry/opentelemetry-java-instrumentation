/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InternalNetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-client">HTTP
 * client attributes</a>. Instrumentation of HTTP client frameworks should extend this class,
 * defining {@link REQUEST} and {@link RESPONSE} with the actual request / response types of the
 * instrumented library. If an attribute is not available in this library, it is appropriate to
 * return {@code null} from the protected attribute methods, but implement as many as possible for
 * best compliance with the OpenTelemetry specification.
 */
public final class HttpClientAttributesExtractor<REQUEST, RESPONSE>
    extends HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, HttpClientAttributesGetter<REQUEST, RESPONSE>>
    implements SpanKeyProvider {

  /** Creates the HTTP client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetClientAttributesGetter<REQUEST, RESPONSE> netAttributesGetter) {
    return builder(httpAttributesGetter, netAttributesGetter).build();
  }

  /**
   * Returns a new {@link HttpClientAttributesExtractorBuilder} that can be used to configure the
   * HTTP client attributes extractor.
   */
  public static <REQUEST, RESPONSE> HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetClientAttributesGetter<REQUEST, RESPONSE> netAttributesGetter) {
    return new HttpClientAttributesExtractorBuilder<>(httpAttributesGetter, netAttributesGetter);
  }

  private final InternalNetClientAttributesExtractor<REQUEST, RESPONSE> internalNetExtractor;

  HttpClientAttributesExtractor(
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetClientAttributesGetter<REQUEST, RESPONSE> netAttributesGetter,
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders) {
    super(httpAttributesGetter, capturedRequestHeaders, capturedResponseHeaders);
    internalNetExtractor =
        new InternalNetClientAttributesExtractor<>(
            netAttributesGetter,
            this::shouldCapturePeerPort,
            new HttpNetNamePortGetter<>(httpAttributesGetter));
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);

    internalSet(
        attributes, SemanticAttributes.HTTP_URL, stripSensitiveData(getter.getUrl(request)));

    internalNetExtractor.onStart(attributes, request);
  }

  private boolean shouldCapturePeerPort(int port, REQUEST request) {
    String url = getter.getUrl(request);
    if (url == null) {
      return true;
    }
    // according to spec: extract if not default (80 for http scheme, 443 for https).
    if ((url.startsWith("http://") && port == 80) || (url.startsWith("https://") && port == 443)) {
      return false;
    }
    return true;
  }

  @Nullable
  private static String stripSensitiveData(@Nullable String url) {
    if (url == null || url.isEmpty()) {
      return url;
    }

    int schemeEndIndex = url.indexOf(':');

    if (schemeEndIndex == -1) {
      // not a valid url
      return url;
    }

    int len = url.length();
    if (len <= schemeEndIndex + 2
        || url.charAt(schemeEndIndex + 1) != '/'
        || url.charAt(schemeEndIndex + 2) != '/') {
      // has no authority component
      return url;
    }

    // look for the end of the authority component:
    //   '/', '?', '#' ==> start of path
    int index;
    int atIndex = -1;
    for (index = schemeEndIndex + 3; index < len; index++) {
      char c = url.charAt(index);

      if (c == '@') {
        atIndex = index;
      }

      if (c == '/' || c == '?' || c == '#') {
        break;
      }
    }

    if (atIndex == -1 || atIndex == len - 1) {
      return url;
    }
    return url.substring(0, schemeEndIndex + 3) + url.substring(atIndex + 1);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    super.onEnd(attributes, context, request, response, error);

    internalSet(attributes, SemanticAttributes.HTTP_FLAVOR, getter.getFlavor(request, response));

    internalNetExtractor.onEnd(attributes, request, response);
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.HTTP_CLIENT;
  }

  private static final class NoopNetClientAttributesGetter<REQUEST, RESPONSE>
      implements NetClientAttributesGetter<REQUEST, RESPONSE> {

    @Nullable
    @Override
    public String getTransport(REQUEST request, @Nullable RESPONSE response) {
      return null;
    }

    @Nullable
    @Override
    public String getPeerName(REQUEST request) {
      return null;
    }

    @Nullable
    @Override
    public Integer getPeerPort(REQUEST request) {
      return null;
    }
  }
}
