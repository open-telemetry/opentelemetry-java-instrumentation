/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents a configuration option available for an instrumentation. This class is internal and is
 * hence not for public use. Its APIs are unstable and can change at any time.
 */
public record ConfigurationOption(
    String name,
    String description,
    @JsonProperty("default") String defaultValue,
    ConfigurationType type) {

  public ConfigurationOption {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(defaultValue, "defaultValue");
    Objects.requireNonNull(type, "type");

    if (name.isBlank() || description.isBlank()) {
      throw new IllegalArgumentException("ConfigurationOption name/description cannot be blank");
    }
  }
}
