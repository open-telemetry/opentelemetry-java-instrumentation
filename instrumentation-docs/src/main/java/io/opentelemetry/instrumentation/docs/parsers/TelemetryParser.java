/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import java.util.Map;
import java.util.Set;

class TelemetryParser {

  // Key is the scope of the module being analyzed, value is a set of additional allowed scopes.
  private static final Map<String, Set<String>> scopeAllowList =
      Map.of("io.opentelemetry.armeria-grpc-1.14", Set.of("io.opentelemetry.grpc-1.6"));

  /**
   * Checks if the given telemetry scope is valid for the specified module scope.
   *
   * <p>If an instrumentation module uses an instrumenter or telemetry class from another module, it
   * might report telemetry with a different scope name, resulting in us excluding it. There are
   * cases where we want to include this data, so we provide this way to override that exclusion
   * filter.
   *
   * @param telemetryScope the scope of the telemetry signal
   * @param moduleScope the scope of the module being analyzed
   * @return true if the telemetry scope is valid for the module, false otherwise
   */
  static boolean scopeIsValid(String telemetryScope, String moduleScope) {
    return telemetryScope.equals(moduleScope)
        || scopeAllowList.getOrDefault(moduleScope, Set.of()).contains(telemetryScope);
  }

  private TelemetryParser() {}
}
