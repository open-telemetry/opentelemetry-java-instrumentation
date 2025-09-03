/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import javax.annotation.Nullable;

/** An interface for getting experimental HTTP client attributes. */
public interface HttpClientExperimentalAttributesGetter<REQUEST, RESPONSE>
    extends HttpClientAttributesGetter<REQUEST, RESPONSE> {

  /**
   * Returns the template used by the http client framework to build the request URL.
   *
   * <p>Examples: {@code /users/:userID?}, {@code {controller}/{action}/{id?}}
   */
  @Nullable
  String getUrlTemplate(REQUEST request);
}
