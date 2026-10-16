/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServletConstants {

  /** Servlet attribute used to indicate that the request has been wrapped to capture its body. */
  public static final String WRAPPED_ATTRIBUTE = "otel.request.body.wrapped";

  /** Servlet attribute name used to store the request body. */
  public static final String REQUEST_BODY_ATTRIBUTE = "otel.request.body";

  private ServletConstants() {}
}
