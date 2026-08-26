/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

/**
 * Marker for a request that pekko-http rejected while parsing it. Such a request never becomes an
 * {@code HttpRequest}, so there is nothing to describe it with beyond the response that pekko-http
 * synthesized.
 */
final class PekkoHttpParsingError {

  static final PekkoHttpParsingError INSTANCE = new PekkoHttpParsingError();

  private PekkoHttpParsingError() {}
}
