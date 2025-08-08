/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import java.util.Map;
import java.util.Set;

abstract class TelemetryParser {

  // If an instrumentation module uses an instrumenter or telemetry class from another module, it
  // might report telemetry with a different scope name, resulting in us excluding it. There are
  // cases where we want to include this data, so we provide this way to override that exclusion
  // filter. The key is the scope of the module being analyzed, the value is a set of additional
  // allowed scopes.
  protected static final Map<String, Set<String>> scopeAllowList =
      Map.of("io.opentelemetry.armeria-grpc-1.14", Set.of("io.opentelemetry.grpc-1.6"));
}
