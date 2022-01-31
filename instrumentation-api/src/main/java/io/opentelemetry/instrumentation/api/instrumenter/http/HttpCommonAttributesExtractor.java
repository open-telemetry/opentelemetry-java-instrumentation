/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpHeaderAttributes.requestAttributeKey;
import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpHeaderAttributes.responseAttributeKey;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#common-attributes">HTTP
 * attributes</a> that are common to client and server instrumentations.
 */
abstract class HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, GETTER extends HttpCommonAttributesGetter<REQUEST, RESPONSE>>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  final GETTER getter;
  private final CapturedHttpHeaders capturedHttpHeaders;

  HttpCommonAttributesExtractor(GETTER getter, CapturedHttpHeaders capturedHttpHeaders) {
    this.getter = getter;
    this.capturedHttpHeaders = capturedHttpHeaders;
  }

  @Override
  public void onStart(AttributesBuilder attributes, REQUEST request) {
    set(attributes, SemanticAttributes.HTTP_METHOD, getter.method(request));
    set(attributes, SemanticAttributes.HTTP_USER_AGENT, userAgent(request));

    for (String name : capturedHttpHeaders.requestHeaders()) {
      List<String> values = getter.requestHeader(request, name);
      if (!values.isEmpty()) {
        set(attributes, requestAttributeKey(name), values);
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    set(
        attributes,
        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
        getter.requestContentLength(request, response));
    set(
        attributes,
        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH_UNCOMPRESSED,
        getter.requestContentLengthUncompressed(request, response));

    if (response != null) {
      Integer statusCode = getter.statusCode(request, response);
      if (statusCode != null && statusCode > 0) {
        set(attributes, SemanticAttributes.HTTP_STATUS_CODE, (long) statusCode);
      }
      set(
          attributes,
          SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
          getter.responseContentLength(request, response));
      set(
          attributes,
          SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH_UNCOMPRESSED,
          getter.responseContentLengthUncompressed(request, response));

      for (String name : capturedHttpHeaders.responseHeaders()) {
        List<String> values = getter.responseHeader(request, response, name);
        if (!values.isEmpty()) {
          set(attributes, responseAttributeKey(name), values);
        }
      }
    }
  }

  @Nullable
  private String userAgent(REQUEST request) {
    return firstHeaderValue(getter.requestHeader(request, "user-agent"));
  }

  @Nullable
  static String firstHeaderValue(List<String> values) {
    return values.isEmpty() ? null : values.get(0);
  }
}
