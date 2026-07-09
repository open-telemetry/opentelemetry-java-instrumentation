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
    @Nullable String name,
    @JsonProperty("declarative_name") @Nullable String declarativeName,
    String description,
    @JsonProperty("default") String defaultValue,
    ConfigurationType type,
    @Nullable List<String> examples,
    @JsonProperty("declarative_type") @Nullable ConfigurationType declarativeType,
    @JsonProperty("declarative_schema") @Nullable DeclarativeSchema declarativeSchema) {

  public ConfigurationOption {
    requireNonNull(description, "description");
    requireNonNull(defaultValue, "defaultValue");
    requireNonNull(type, "type");

    // Most configs are backed by a flat system property (name). Declarative-only configs (such as
    // the url_template_rules structured list) have no flat property and rely on declarative_name.
    if (name == null && declarativeName == null) {
      throw new IllegalArgumentException(
          "ConfigurationOption must have a name or a declarative_name");
    }
    if ((name != null && name.isBlank()) || description.isBlank()) {
      throw new IllegalArgumentException("ConfigurationOption name/description cannot be blank");
    }

    if (declarativeType == ConfigurationType.STRUCTURED_LIST) {
      if (declarativeSchema == null
          || declarativeSchema.properties() == null
          || declarativeSchema.properties().isEmpty()) {
        throw new IllegalArgumentException(
            "structured_list configuration must define a declarative_schema with properties");
      }
      if (declarativeSchema.required() != null
          && !declarativeSchema.properties().keySet().containsAll(declarativeSchema.required())) {
        throw new IllegalArgumentException(
            "declarative_schema required keys must be a subset of its properties");
      }
    }
  }

  public ConfigurationOption(
      String name, String description, String defaultValue, ConfigurationType type) {
    this(name, null, description, defaultValue, type, null, null, null);
  }
}
