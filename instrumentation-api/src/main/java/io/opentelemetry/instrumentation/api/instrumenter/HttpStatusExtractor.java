/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.tracer.HttpStatusConverter;

final class HttpStatusExtractor<REQUEST, RESPONSE> implements StatusExtractor<REQUEST, RESPONSE> {

  private final HttpAttributesExtractor<REQUEST, RESPONSE> attributesExtractor;

  protected HttpStatusExtractor(HttpAttributesExtractor<REQUEST, RESPONSE> attributesExtractor) {
    this.attributesExtractor = attributesExtractor;
  }

  @Override
  public StatusCode extract(REQUEST request, RESPONSE response, Throwable error) {
    Long statusCode = attributesExtractor.statusCode(request, response);
    if (statusCode != null) {
      return HttpStatusConverter.statusFromHttpStatus((int) (long) statusCode);
    }
    return StatusExtractor.getDefault().extract(request, response, error);
  }
}
