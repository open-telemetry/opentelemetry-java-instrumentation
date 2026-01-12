/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

/**
 * Represents functionality of instrumentations. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public enum InstrumentationFeature {
  HTTP_ROUTE,
  CONTEXT_PROPAGATION,
  AUTO_INSTRUMENTATION_SHIM,
  CONTROLLER_SPANS,
  VIEW_SPANS,
  LOGGING_BRIDGE
}
