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
      Map.of(
          // armeria-grpc uses grpc-1.6 instrumenter.
          "io.opentelemetry.armeria-grpc-1.14", Set.of("io.opentelemetry.grpc-1.6"),
          // couchbase-2.6 extends couchbase-2.0 instrumentation with more attributes.
          "io.opentelemetry.couchbase-2.6", Set.of("io.opentelemetry.couchbase-2.0"),
          // elasticsearch-rest-7.0 extends elasticsearch-api-client-7.16 with more attributes.
          "io.opentelemetry.elasticsearch-api-client-7.16",
              Set.of("io.opentelemetry.elasticsearch-rest-7.0"));

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
