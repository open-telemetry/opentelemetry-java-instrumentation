/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.tracer.HttpStatusConverter;

final class HttpSpanStatusExtractor<REQUEST, RESPONSE>
    implements SpanStatusExtractor<REQUEST, RESPONSE> {

  private final HttpAttributesExtractor<REQUEST, RESPONSE> attributesExtractor;

  protected HttpSpanStatusExtractor(
      HttpAttributesExtractor<REQUEST, RESPONSE> attributesExtractor) {
    this.attributesExtractor = attributesExtractor;
  }

  @Override
  public StatusCode extract(REQUEST request, RESPONSE response, Throwable error) {
    Long statusCode = attributesExtractor.statusCode(request, response);
    if (statusCode != null) {
      return HttpStatusConverter.statusFromHttpStatus((int) (long) statusCode);
    }
    return SpanStatusExtractor.getDefault().extract(request, response, error);
  }
}
