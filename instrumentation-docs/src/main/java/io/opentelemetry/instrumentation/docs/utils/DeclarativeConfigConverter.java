/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts flat property names (e.g., "otel.instrumentation.grpc.emit-message-events") to
 * declarative configuration paths (e.g., "instrumentation.java.grpc.emit_message_events").
 */
public class DeclarativeConfigConverter {

  /**
   * Special mappings from flat property names to declarative paths. These are inverted from
   * ConfigPropertiesBackedDeclarativeConfigProperties.SPECIAL_MAPPINGS.
   */
  private static final Map<String, String> SPECIAL_MAPPINGS = createSpecialMappings();

  private static Map<String, String> createSpecialMappings() {
    Map<String, String> map = new HashMap<>();

    // HTTP general settings
    map.put(
        "otel.instrumentation.http.client.capture-request-headers",
        "general.http.client.request_captured_headers");
    map.put(
        "otel.instrumentation.http.client.capture-response-headers",
        "general.http.client.response_captured_headers");
    map.put(
        "otel.instrumentation.http.server.capture-request-headers",
        "general.http.server.request_captured_headers");
    map.put(
        "otel.instrumentation.http.server.capture-response-headers",
        "general.http.server.response_captured_headers");

    // Semconv stability
    map.put("otel.semconv-stability.opt-in", "general.semconv_stability.opt_in");

    // JMX (special case - no "instrumentation" in flat property)
    map.put("otel.jmx.enabled", "java.jmx.enabled");
    map.put("otel.jmx.groovy-script", "java.jmx.groovy_script");
    map.put("otel.jmx.service-url", "java.jmx.service_url");
    map.put("otel.jmx.target-system", "java.jmx.target_system");
    map.put("otel.jmx.username", "java.jmx.username");
    map.put("otel.jmx.password", "java.jmx.password");
    map.put("otel.jmx.remote-profile", "java.jmx.remote_profile");
    map.put("otel.jmx.realm", "java.jmx.realm");
    map.put("otel.jmx.interval-milliseconds", "java.jmx.interval_milliseconds");

    return map;
  }

  /**
   * Converts a flat property name to a declarative configuration path.
   *
   * @param flatProperty the flat property name (e.g.,
   *     "otel.instrumentation.grpc.emit-message-events")
   * @return the declarative path (e.g., "instrumentation.java.grpc.emit_message_events")
   */
  public static String toDeclarativePath(String flatProperty) {
    if (SPECIAL_MAPPINGS.containsKey(flatProperty)) {
      return SPECIAL_MAPPINGS.get(flatProperty);
    }

    // Handle otel.jmx.* properties (no "instrumentation" segment)
    if (flatProperty.startsWith("otel.jmx.")) {
      String remainder = flatProperty.substring("otel.jmx.".length());
      return "java.jmx." + hyphenToUnderscore(remainder);
    }

    // Standard conversion: otel.instrumentation.<module>.<path>
    if (flatProperty.startsWith("otel.instrumentation.")) {
      String remainder = flatProperty.substring("otel.instrumentation.".length());

      int firstDot = remainder.indexOf('.');
      if (firstDot == -1) {
        // Just the module name, no path (e.g., "otel.instrumentation.grpc")
        return "instrumentation.java." + hyphenToUnderscore(remainder);
      }

      String module = remainder.substring(0, firstDot);
      String path = remainder.substring(firstDot + 1);

      String developmentSuffix = "";
      if (path.contains("experimental")) {
        developmentSuffix = "/development";
        path =
            path.replace("-experimental", "")
                .replace("experimental-", "")
                .replace("experimental", "");
        // Clean up any double hyphens or trailing/leading hyphens
        path = path.replace("--", "-").replaceAll("^-|-$", "");
      }

      String convertedModule = hyphenToUnderscore(module);
      String convertedPath = hyphenToUnderscore(path);

      return "instrumentation.java." + convertedModule + "." + convertedPath + developmentSuffix;
    }

    // Fallback: just convert hyphens to underscores
    return hyphenToUnderscore(flatProperty);
  }

  /**
   * Converts hyphens to underscores in a string.
   *
   * @param str the string to convert
   * @return the converted string
   */
  private static String hyphenToUnderscore(String str) {
    return str.replace('-', '_');
  }

  private DeclarativeConfigConverter() {}
}
