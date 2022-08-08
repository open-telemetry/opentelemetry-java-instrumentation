/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.SemanticAttributes.HTTP_REQUEST_HEADERS;
import static io.opentelemetry.instrumentation.api.instrumenter.http.SemanticAttributes.HTTP_RESPONSE_HEADERS;
import static java.util.logging.Level.FINE;
import static io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeadersUtil.lowercase;
import static io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeadersUtil.requestAttributeKey;
import static io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeadersUtil.responseAttributeKey;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#common-attributes">HTTP
 * attributes</a> that are common to client and server instrumentations.
 */
abstract class HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, GETTER extends HttpCommonAttributesGetter<REQUEST, RESPONSE>>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private static final Logger logger = Logger.getLogger(HttpCommonAttributesExtractor.class.getName());

  public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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
    internalSet(attributes, SemanticAttributes.HTTP_METHOD, getter.method(request));
    internalSet(attributes, SemanticAttributes.HTTP_USER_AGENT, userAgent(request));

    Map<String, String> reqHeaders = requestHeaders(request, null);
    if (!reqHeaders.isEmpty()) {
      internalSet(attributes, HTTP_REQUEST_HEADERS, toJsonString(reqHeaders));
    }

    for (String name : capturedRequestHeaders) {
      List<String> values = getter.requestHeader(request, name);
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

    internalSet(
        attributes,
        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
        getter.requestContentLength(request, response));
    internalSet(
        attributes,
        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH_UNCOMPRESSED,
        getter.requestContentLengthUncompressed(request, response));

    if (response != null) {
      Integer statusCode = getter.statusCode(request, response);
      if (statusCode != null && statusCode > 0) {
        internalSet(attributes, SemanticAttributes.HTTP_STATUS_CODE, (long) statusCode);
      }
      internalSet(
          attributes,
          SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
          getter.responseContentLength(request, response));
      internalSet(
          attributes,
          SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH_UNCOMPRESSED,
          getter.responseContentLengthUncompressed(request, response));

      Map<String, String> resHeaders = responseHeaders(request, response);
      if (!resHeaders.isEmpty()) {
        internalSet(
            attributes,
            HTTP_RESPONSE_HEADERS,
            toJsonString(resHeaders)
        );
      }

      for (String name : capturedResponseHeaders) {
        List<String> values = getter.responseHeader(request, response, name);
        if (!values.isEmpty()) {
          internalSet(attributes, responseAttributeKey(name), values);
        }
      }
    }
  }

  Map<String, String> requestHeaders(REQUEST request,  @Nullable RESPONSE response) {
    return getter.requestHeaders(request, response);
  }

  Map<String, String> responseHeaders(REQUEST request, @Nullable RESPONSE response) {
    return getter.responseHeaders(request, response);
  }

  @Nullable
  private String userAgent(REQUEST request) {
    return firstHeaderValue(getter.requestHeader(request, "user-agent"));
  }

  @Nullable
  static String firstHeaderValue(List<String> values) {
    return values.isEmpty() ? null : values.get(0);
  }

  static String toJsonString(Map<String, String> m) {
    try {
      return JSON_MAPPER.writeValueAsString(m);
    } catch (IOException e) {
      logger.log(FINE, "Failed converting headers map to json string", e);
      return "{}";
    }
  }
}
