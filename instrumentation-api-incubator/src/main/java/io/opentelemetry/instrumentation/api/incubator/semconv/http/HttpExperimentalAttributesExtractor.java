/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

public final class HttpExperimentalAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from HttpIncubatingAttributes
  static final AttributeKey<Long> HTTP_REQUEST_BODY_SIZE =
      AttributeKey.longKey("http.request.body.size");
  static final AttributeKey<Long> HTTP_RESPONSE_BODY_SIZE =
      AttributeKey.longKey("http.response.body.size");

  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new HttpExperimentalAttributesExtractor<>(getter);
  }

  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return new HttpExperimentalAttributesExtractor<>(getter);
  }

  private final HttpCommonAttributesGetter<REQUEST, RESPONSE> getter;

  private HttpExperimentalAttributesExtractor(
      HttpCommonAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
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

    Long requestBodySize = requestBodySize(request);
    internalSet(attributes, HTTP_REQUEST_BODY_SIZE, requestBodySize);

    if (response != null) {
      Long responseBodySize = responseBodySize(request, response);
      internalSet(attributes, HTTP_RESPONSE_BODY_SIZE, responseBodySize);
    }
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
}
