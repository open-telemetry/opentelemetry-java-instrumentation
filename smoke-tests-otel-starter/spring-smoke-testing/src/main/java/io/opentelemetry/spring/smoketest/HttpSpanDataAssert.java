/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.ResourceAssert;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractLongAssert;

public final class HttpSpanDataAssert {

  private final SpanDataAssert span;

  private HttpSpanDataAssert(SpanDataAssert span) {
    this.span = span;
  }

  public static HttpSpanDataAssert create(SpanDataAssert serverSpan) {
    return new HttpSpanDataAssert(serverSpan);
  }

  @CanIgnoreReturnValue
  public HttpSpanDataAssert assertClientGetRequest(String path) {
    span.hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfying(
            satisfies(UrlAttributes.URL_FULL, a -> a.endsWith(path)),
            // this attribute is set by the experimental http instrumentation
            satisfies(
                HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE,
                AbstractLongAssert::isNotNegative));
    return this;
  }

  @CanIgnoreReturnValue
  public HttpSpanDataAssert assertServerGetRequest(String route) {
    span.hasKind(SpanKind.SERVER)
        .hasAttributesSatisfying(
            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200L),
            equalTo(HttpAttributes.HTTP_ROUTE, route),
            // this attribute is set by the experimental http instrumentation
            satisfies(
                HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE,
                AbstractLongAssert::isNotNegative));
    return this;
  }

  public SpanDataAssert hasResourceSatisfying(Consumer<ResourceAssert> resource) {
    return span.hasResourceSatisfying(resource);
  }
}
