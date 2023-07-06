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
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.function.ToIntFunction;
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
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final InternalServerAttributesExtractor<REQUEST, RESPONSE> internalServerExtractor;
  private final ToIntFunction<Context> resendCountIncrementer;

  HttpClientAttributesExtractor(
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetClientAttributesGetter<REQUEST, RESPONSE> netAttributesGetter,
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders) {
    this(
        httpAttributesGetter,
        netAttributesGetter,
        capturedRequestHeaders,
        capturedResponseHeaders,
        HttpClientResend::getAndIncrement);
  }

  // visible for tests
  HttpClientAttributesExtractor(
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetClientAttributesGetter<REQUEST, RESPONSE> netAttributesGetter,
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders,
      ToIntFunction<Context> resendCountIncrementer) {
    super(httpAttributesGetter, capturedRequestHeaders, capturedResponseHeaders);
    HttpNetAddressPortExtractor<REQUEST> addressPortExtractor =
        new HttpNetAddressPortExtractor<>(httpAttributesGetter);
    internalNetExtractor =
        new InternalNetClientAttributesExtractor<>(
            netAttributesGetter, addressPortExtractor, SemconvStability.emitOldHttpSemconv());
    internalNetworkExtractor =
        new InternalNetworkAttributesExtractor<>(
            netAttributesGetter,
            HttpNetworkTransportFilter.INSTANCE,
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv());
    internalServerExtractor =
        new InternalServerAttributesExtractor<>(
            netAttributesGetter,
            this::shouldCaptureServerPort,
            addressPortExtractor,
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv(),
            InternalServerAttributesExtractor.Mode.PEER);
    this.resendCountIncrementer = resendCountIncrementer;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);

    internalServerExtractor.onStart(attributes, request);

    String fullUrl = stripSensitiveData(getter.getUrlFull(request));
    if (SemconvStability.emitStableHttpSemconv()) {
      internalSet(attributes, UrlAttributes.URL_FULL, fullUrl);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      internalSet(attributes, SemanticAttributes.HTTP_URL, fullUrl);
    }
  }

  private boolean shouldCaptureServerPort(int port, REQUEST request) {
    String url = getter.getUrlFull(request);
    if (url == null) {
      return true;
    }
    // according to spec: extract if not default (80 for http scheme, 443 for https).
    if ((url.startsWith("http://") && port == 80) || (url.startsWith("https://") && port == 443)) {
      return false;
    }
    return true;
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    super.onEnd(attributes, context, request, response, error);

    internalNetExtractor.onEnd(attributes, request, response);
    internalNetworkExtractor.onEnd(attributes, request, response);
    internalServerExtractor.onEnd(attributes, request, response);

    int resendCount = resendCountIncrementer.applyAsInt(context);
    if (resendCount > 0) {
      attributes.put(SemanticAttributes.HTTP_RESEND_COUNT, resendCount);
    }
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.HTTP_CLIENT;
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
    return url.substring(0, schemeEndIndex + 3) + "REDACTED:REDACTED" + url.substring(atIndex);
  }
}
