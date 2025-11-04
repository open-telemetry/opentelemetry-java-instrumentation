/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Map.entry;

import java.util.Map;
import java.util.Set;

class TelemetryParser {

  // Key is the scope of the module being analyzed, value is a set of additional allowed scopes.
  private static final Map<String, Set<String>> scopeAllowList;

  static {
    scopeAllowList =
        Map.ofEntries(
            // armeria-grpc uses grpc-1.6 instrumenter.
            entry("io.opentelemetry.armeria-grpc-1.14", singleton("io.opentelemetry.grpc-1.6")),
            // couchbase-2.6 extends couchbase-2.0 instrumentation with more attributes.
            entry("io.opentelemetry.couchbase-2.6", singleton("io.opentelemetry.couchbase-2.0")),
            // elasticsearch-rest-7.0 extends elasticsearch-api-client-7.16 with more attributes.
            entry(
                "io.opentelemetry.elasticsearch-api-client-7.16",
                singleton("io.opentelemetry.elasticsearch-rest-7.0")),
            // jaxrs instrumentations add attributes to the jaxrs-2.0-annotations scope.
            entry(
                "io.opentelemetry.jaxrs-2.0-cxf-3.2",
                singleton("io.opentelemetry.jaxrs-2.0-annotations")),
            entry(
                "io.opentelemetry.jaxrs-2.0-jersey-2.0",
                singleton("io.opentelemetry.jaxrs-2.0-annotations")),
            entry(
                "io.opentelemetry.jaxrs-2.0-resteasy-3.0",
                singleton("io.opentelemetry.jaxrs-2.0-annotations")),
            entry(
                "io.opentelemetry.jaxrs-2.0-resteasy-3.1",
                singleton("io.opentelemetry.jaxrs-2.0-annotations")),
            entry(
                "io.opentelemetry.jaxrs-3.0-jersey-3.0",
                singleton("io.opentelemetry.jaxrs-3.0-annotations")),
            entry(
                "io.opentelemetry.jaxrs-3.0-resteasy-6.0",
                singleton("io.opentelemetry.jaxrs-3.0-annotations")),
            // couchbase-3.x instrumentations are auto-instrumentation shims
            entry(
                "io.opentelemetry.couchbase-3.1",
                singleton("io.opentelemetry.javaagent.couchbase-3.1")),
            entry("io.opentelemetry.couchbase-3.1.6", singleton("com.couchbase.client.jvm")),
            entry("io.opentelemetry.couchbase-3.2", singleton("com.couchbase.client.jvm")),
            entry("io.opentelemetry.couchbase-3.4", singleton("com.couchbase.client.jvm")));
  }

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
        || scopeAllowList.getOrDefault(moduleScope, emptySet()).contains(telemetryScope);
  }

  private TelemetryParser() {}
}
