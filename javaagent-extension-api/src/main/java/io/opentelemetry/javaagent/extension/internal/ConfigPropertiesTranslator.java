/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("NonApiType")
class ConfigPropertiesTranslator {
  // lookup order matters - we choose the first match
  private final LinkedHashMap<String, String> translationMap;
  private final Map<String, Object> fixedValues;

  ConfigPropertiesTranslator(
      LinkedHashMap<String, String> translationMap, Map<String, Object> fixedValues) {
    this.translationMap = translationMap;
    this.fixedValues = fixedValues;
  }

  String translateProperty(String property) {
    for (Map.Entry<String, String> entry : translationMap.entrySet()) {
      if (property.startsWith(entry.getKey())) {
        return entry.getValue() + property.substring(entry.getKey().length());
      }
    }
    return property;
  }

  @Nullable
  public Object get(String propertyName) {
    return fixedValues.get(propertyName);
  }
}
