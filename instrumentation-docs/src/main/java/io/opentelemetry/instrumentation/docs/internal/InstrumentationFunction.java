/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents functionality of instrumentations. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public enum InstrumentationFunction {
  HTTP_ROUTE_ENRICHER("http-route-enricher"),
  LIBRARY_DOMAIN_ENRICHER("library-domain-enricher"),
  EXPERIMENTAL_ONLY("experimental-only"),
  CONTEXT_PROPAGATOR("context-propagator"),
  UPSTREAM_ADAPTER("upstream-adapter"),
  CONFIGURATION("configuration"),
  CONTROLLER_SPANS("controller-spans"),
  VIEW_SPANS("view-spans"),
  SYSTEM_METRICS("system-metrics");

  private final String name;

  InstrumentationFunction(String name) {
    this.name = name;
  }

  @JsonValue
  public String getName() {
    return name;
  }

  @JsonCreator
  public static InstrumentationFunction fromYamlName(String yamlName) {
    for (InstrumentationFunction function : values()) {
      if (function.name.equals(yamlName)) {
        return function;
      }
    }
    throw new IllegalArgumentException("Unknown instrumentation function: " + yamlName);
  }
}
