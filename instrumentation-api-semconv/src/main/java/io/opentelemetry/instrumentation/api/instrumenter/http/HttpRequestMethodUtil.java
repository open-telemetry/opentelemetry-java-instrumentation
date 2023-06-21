/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

import java.util.HashSet;
import java.util.Set;

final class HttpRequestMethodUtil {

  static final Set<String> KNOWN_METHODS =
      unmodifiableSet(
          new HashSet<>(
              asList(
                  "CONNECT", "DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT", "TRACE")));

  static final String _OTHER = "_OTHER";

  private HttpRequestMethodUtil() {}
}
