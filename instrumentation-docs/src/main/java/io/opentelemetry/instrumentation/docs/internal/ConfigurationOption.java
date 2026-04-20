/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents a configuration option available for an instrumentation. This class is internal and is
 * hence not for public use. Its APIs are unstable and can change at any time.
 */
public record ConfigurationOption(
    String name,
    @JsonProperty("declarative_name") @Nullable String declarativeName,
    String description,
    @JsonProperty("default") String defaultValue,
    ConfigurationType type,
    @Nullable List<String> examples) {

  public ConfigurationOption {
    requireNonNull(name, "name");
    requireNonNull(description, "description");
    requireNonNull(defaultValue, "defaultValue");
    requireNonNull(type, "type");

    if (name.isBlank() || description.isBlank()) {
      throw new IllegalArgumentException("ConfigurationOption name/description cannot be blank");
    }
  }

  public ConfigurationOption(
      String name, String description, String defaultValue, ConfigurationType type) {
    this(name, null, description, defaultValue, type, null);
  }
}
