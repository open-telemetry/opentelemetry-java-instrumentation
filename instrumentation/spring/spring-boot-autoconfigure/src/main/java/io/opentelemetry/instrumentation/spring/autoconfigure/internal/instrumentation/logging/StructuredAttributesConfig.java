/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.EarlyConfig;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;

final class StructuredAttributesConfig {

  private static final String INCLUDED =
      "otel.instrumentation.common.logging.structured-attributes.included";
  private static final String EXCLUDED =
      "otel.instrumentation.common.logging.structured-attributes.excluded";

  static IncludeExclude getSelector(ConfigurableEnvironment environment) {
    List<String> included = getListProperty(environment, INCLUDED);
    List<String> excluded = getListProperty(environment, EXCLUDED);
    return IncludeExclude.builder()
        .setIncluded(
            isEmpty(included) && isEmpty(excluded)
                ? singletonList("*")
                : included == null ? emptyList() : included)
        .setExcluded(excluded == null ? emptyList() : excluded)
        .build();
  }

  private static boolean isEmpty(@Nullable List<String> values) {
    return values == null || values.isEmpty();
  }

  @Nullable
  private static List<String> getListProperty(
      ConfigurableEnvironment environment, String property) {
    String propertyName = getEnvironmentPropertyName(environment, property);
    String value = environment.getProperty(propertyName, String.class);
    if (value != null) {
      return split(value);
    }

    List<String> values = new ArrayList<>();
    boolean configured = false;
    for (int i = 0; ; i++) {
      String item = environment.getProperty(propertyName + "[" + i + "]", String.class);
      if (item == null) {
        break;
      }
      configured = true;
      item = item.trim();
      if (!item.isEmpty()) {
        values.add(item);
      }
    }
    return configured ? values : null;
  }

  private static List<String> split(String value) {
    List<String> values = new ArrayList<>();
    for (String item : value.split(",")) {
      item = item.trim();
      if (!item.isEmpty()) {
        values.add(item);
      }
    }
    return values;
  }

  private static String getEnvironmentPropertyName(
      ConfigurableEnvironment environment, String property) {
    if (EarlyConfig.isDeclarativeConfig(environment)) {
      StringBuilder declarativeProperty = new StringBuilder();
      for (String segment : property.substring("otel.instrumentation.".length()).split("\\.")) {
        if (declarativeProperty.length() > 0) {
          declarativeProperty.append('.');
        }
        declarativeProperty.append(segment.replace('-', '_'));
      }
      return "otel.instrumentation/development.java." + declarativeProperty;
    }
    return property;
  }

  private StructuredAttributesConfig() {}
}
