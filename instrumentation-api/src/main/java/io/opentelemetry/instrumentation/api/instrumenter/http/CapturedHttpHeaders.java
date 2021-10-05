/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.emptyList;

import com.google.auto.value.AutoValue;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Represents the configuration that specifies which HTTP request/response headers should be
 * captured as span attributes.
 */
@AutoValue
public abstract class CapturedHttpHeaders {

  private static final CapturedHttpHeaders EMPTY = create(emptyList(), emptyList());

  /** Don't capture any HTTP headers as span attributes. */
  public static CapturedHttpHeaders empty() {
    return EMPTY;
  }

  /**
   * Captures the configured HTTP request and response headers as span attributes as described in <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-request-and-response-headers">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP request header values will be captured under the {@code http.request.header.<name>}
   * attribute key. The HTTP response header values will be captured under the {@code
   * http.response.header.<name>} attribute key. The {@code <name>} part in the attribute key is the
   * normalized header name: lowercase, with dashes replaced by underscores.
   *
   * @param capturedRequestHeaders A list of HTTP request header names that are to be captured as
   *     span attributes.
   * @param capturedResponseHeaders A list of HTTP response header names that are to be captured as
   *     span attributes.
   */
  public static CapturedHttpHeaders create(
      List<String> capturedRequestHeaders, List<String> capturedResponseHeaders) {
    return new AutoValue_CapturedHttpHeaders(
        lowercase(capturedRequestHeaders), lowercase(capturedResponseHeaders));
  }

  private static List<String> lowercase(List<String> names) {
    return names.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
  }

  /** Returns the list of HTTP request header names that are to be captured as span attributes. */
  public abstract List<String> requestHeaders();

  /** Returns the list of HTTP response header names that are to be captured as span attributes. */
  public abstract List<String> responseHeaders();

  CapturedHttpHeaders() {}
}
