/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeadersUtil.lowercase;
import static io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeadersUtil.requestAttributeKey;
import static io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeadersUtil.responseAttributeKey;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.FallbackNamePortGetter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#common-attributes">HTTP
 * attributes</a> that are common to client and server instrumentations.
 */
abstract class HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, GETTER extends HttpCommonAttributesGetter<REQUEST, RESPONSE>>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private static final Logger logger = Logger.getLogger(HttpCommonAttributesGetter.class.getName());

  final GETTER getter;
  private final List<String> capturedRequestHeaders;
  private final List<String> capturedResponseHeaders;

  HttpCommonAttributesExtractor(
      GETTER getter, List<String> capturedRequestHeaders, List<String> capturedResponseHeaders) {
    this.getter = getter;
    this.capturedRequestHeaders = lowercase(capturedRequestHeaders);
    this.capturedResponseHeaders = lowercase(capturedResponseHeaders);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    String method = getter.getHttpRequestMethod(request);
    if (SemconvStability.emitStableHttpSemconv()) {
      internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD, method);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      internalSet(attributes, SemanticAttributes.HTTP_METHOD, method);
    }
    internalSet(attributes, SemanticAttributes.USER_AGENT_ORIGINAL, userAgent(request));

    for (String name : capturedRequestHeaders) {
      List<String> values = getter.getHttpRequestHeader(request, name);
      if (!values.isEmpty()) {
        internalSet(attributes, requestAttributeKey(name), values);
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    Long requestBodySize = requestBodySize(request);
    if (SemconvStability.emitStableHttpSemconv()) {
      internalSet(attributes, HttpAttributes.HTTP_REQUEST_BODY_SIZE, requestBodySize);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      internalSet(attributes, SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestBodySize);
    }

    if (response != null) {
      Integer statusCode = getter.getHttpResponseStatusCode(request, response, error);
      if (statusCode != null && statusCode > 0) {
        if (SemconvStability.emitStableHttpSemconv()) {
          internalSet(attributes, HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) statusCode);
        }
        if (SemconvStability.emitOldHttpSemconv()) {
          internalSet(attributes, SemanticAttributes.HTTP_STATUS_CODE, (long) statusCode);
        }
      }

      Long responseBodySize = responseBodySize(request, response);
      if (SemconvStability.emitStableHttpSemconv()) {
        internalSet(attributes, HttpAttributes.HTTP_RESPONSE_BODY_SIZE, responseBodySize);
      }
      if (SemconvStability.emitOldHttpSemconv()) {
        internalSet(attributes, SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, responseBodySize);
      }

      for (String name : capturedResponseHeaders) {
        List<String> values = getter.getHttpResponseHeader(request, response, name);
        if (!values.isEmpty()) {
          internalSet(attributes, responseAttributeKey(name), values);
        }
      }
    }
  }

  @Nullable
  private String userAgent(REQUEST request) {
    return firstHeaderValue(getter.getHttpRequestHeader(request, "user-agent"));
  }

  @Nullable
  private Long requestBodySize(REQUEST request) {
    return parseNumber(firstHeaderValue(getter.getHttpRequestHeader(request, "content-length")));
  }

  @Nullable
  private Long responseBodySize(REQUEST request, RESPONSE response) {
    return parseNumber(
        firstHeaderValue(getter.getHttpResponseHeader(request, response, "content-length")));
  }

  @Nullable
  static String firstHeaderValue(List<String> values) {
    return values.isEmpty() ? null : values.get(0);
  }

  @Nullable
  private static Long parseNumber(@Nullable String number) {
    if (number == null) {
      return null;
    }
    try {
      return Long.parseLong(number);
    } catch (NumberFormatException e) {
      // not a number
      return null;
    }
  }

  static final class HttpNetNamePortGetter<REQUEST> implements FallbackNamePortGetter<REQUEST> {

    private final HttpCommonAttributesGetter<REQUEST, ?> getter;

    HttpNetNamePortGetter(HttpCommonAttributesGetter<REQUEST, ?> getter) {
      this.getter = getter;
    }

    @Nullable
    @Override
    public String name(REQUEST request) {
      String host = firstHeaderValue(getter.getHttpRequestHeader(request, "host"));
      if (host == null) {
        return null;
      }
      int hostHeaderSeparator = host.indexOf(':');
      return hostHeaderSeparator == -1 ? host : host.substring(0, hostHeaderSeparator);
    }

    @Nullable
    @Override
    public Integer port(REQUEST request) {
      String host = firstHeaderValue(getter.getHttpRequestHeader(request, "host"));
      if (host == null) {
        return null;
      }
      int hostHeaderSeparator = host.indexOf(':');
      if (hostHeaderSeparator == -1) {
        return null;
      }
      try {
        return Integer.parseInt(host.substring(hostHeaderSeparator + 1));
      } catch (NumberFormatException e) {
        logger.log(FINE, e.getMessage(), e);
      }
      return null;
    }
  }
}
