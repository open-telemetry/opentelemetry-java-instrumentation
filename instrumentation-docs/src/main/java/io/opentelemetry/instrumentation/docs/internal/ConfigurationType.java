/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum ConfigurationType {
  BOOLEAN("boolean"),
  STRING("string"),
  INT("int"),
  MAP("map"),
  LIST("list");

  ConfigurationType(String unused) {}

  @JsonCreator
  public static ConfigurationType from(String value) {
    return ConfigurationType.valueOf(value.toUpperCase(Locale.ROOT));
  }

  @JsonValue
  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT);
  }
}
