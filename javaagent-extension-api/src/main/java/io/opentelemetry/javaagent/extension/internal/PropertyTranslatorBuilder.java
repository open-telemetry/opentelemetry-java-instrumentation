/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class PropertyTranslatorBuilder {
  private final LinkedHashMap<String, String> translationMap = new LinkedHashMap<>();
  private final Map<String, Object> fixedValues = new LinkedHashMap<>();

  PropertyTranslatorBuilder() {}

  public PropertyTranslatorBuilder addTranslation(String propertyName, String yamlPath) {
    translationMap.put(propertyName, yamlPath);
    return this;
  }

  public PropertyTranslatorBuilder addFixedValue(String propertyName, Object value) {
    fixedValues.put(propertyName, value);
    return this;
  }

  PropertyTranslator build() {
    return new PropertyTranslator(translationMap, fixedValues);
  }
}
