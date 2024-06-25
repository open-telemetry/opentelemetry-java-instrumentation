/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import org.assertj.core.api.AbstractLongAssert;

public class SpringTestUtil {

  private static final AttributeKey<Long> BODY_SIZE =
      AttributeKey.longKey("http.response.body.size");

  private SpringTestUtil() {}

  public static void assertClientSpan(SpanDataAssert span, String path) {
    span.hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfying(
            OpenTelemetryAssertions.satisfies(UrlAttributes.URL_FULL, a -> a.endsWith(path)),
            // this attribute is set by the experimental http instrumentation
            OpenTelemetryAssertions.satisfies(BODY_SIZE, AbstractLongAssert::isNotNegative));
  }

  @CanIgnoreReturnValue
  public static SpanDataAssert assertServerSpan(SpanDataAssert span, String route) {
    span.hasKind(SpanKind.SERVER)
        .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
        .hasAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200L)
        .hasAttribute(HttpAttributes.HTTP_ROUTE, route)
        .hasAttributesSatisfying(
            // this attribute is set by the experimental http instrumentation
            OpenTelemetryAssertions.satisfies(BODY_SIZE, AbstractLongAssert::isNotNegative));
    return span;
  }
}
