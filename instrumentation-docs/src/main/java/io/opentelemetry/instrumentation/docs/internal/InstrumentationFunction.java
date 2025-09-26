/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

/**
 * Represents functionality of instrumentations. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public enum InstrumentationFunction {
  HTTP_ROUTE_ENRICHER,
  LIBRARY_DOMAIN_ENRICHER,
  EXPERIMENTAL_ONLY,
  CONTEXT_PROPAGATION,
  UPSTREAM_ADAPTER,
  CONFIGURATION,
  CONTROLLER_SPANS,
  VIEW_SPANS,
  SYSTEM_METRICS
}
