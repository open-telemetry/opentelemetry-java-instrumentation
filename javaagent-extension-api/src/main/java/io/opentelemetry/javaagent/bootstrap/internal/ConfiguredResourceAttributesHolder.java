/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ConfiguredResourceAttributesHolder {

  private static final Map<String, String> resourceAttributes = new HashMap<>();

  public static Map<String, String> getResourceAttributes() {
    return resourceAttributes;
  }

  public static void initialize(Attributes resourceAttribute) {
    List<String> mdcResourceAttributes =
        DeclarativeConfigUtil.getList(
                GlobalOpenTelemetry.get(), "java", "common", "mdc", "resource_attributes")
            .orElse(Collections.emptyList());
    for (String key : mdcResourceAttributes) {
      String value = resourceAttribute.get(stringKey(key));
      if (value != null) {
        ConfiguredResourceAttributesHolder.resourceAttributes.put(key, value);
      }
    }
  }

  @Nullable
  public static String getAttributeValue(String key) {
    return resourceAttributes.get(key);
  }

  private ConfiguredResourceAttributesHolder() {}
}
