/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.ExceptionEventExtractor;

/**
 * {@link ExceptionEventExtractor} constants for HTTP client and server instrumentations.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class HttpExceptionEventExtractors {

  /** Exception event extractor for HTTP client spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> client() {
    return ExceptionEventExtractor.create("http.client.request.exception", Severity.WARN);
  }

  /** Exception event extractor for HTTP server spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> server() {
    return ExceptionEventExtractor.create("http.server.request.exception", Severity.ERROR);
  }

  private HttpExceptionEventExtractors() {}
}
