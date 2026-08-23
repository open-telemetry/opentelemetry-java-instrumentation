/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonProperty("declarative_schema") @Nullable DeclarativeSchema declarativeSchema,
    @Nullable String ref,
    // The definition id is assigned internally via withId() during registry resolution; it must
    // never be supplied from metadata.yaml, otherwise an inline option could claim a registry id
    // and
    // overwrite the shared definition in buildDefinitionCatalog.
    @JsonIgnore @Nullable String id) {

  public ConfigurationOption {
    // A reference entry (`- ref: <id>`) in a metadata.yaml carries only the ref id; it is resolved
    // against the shared configuration registry before use, so it skips the field validation that
    // applies to fully-specified options. Because resolve() replaces the entire entry with the
    // shared definition, any other field set alongside the ref would be silently discarded. Reject
    // such entries (and a blank ref) so authors cannot publish settings that appear effective but
    // are ignored.
    if (ref != null) {
      if (ref.isBlank()) {
        throw new IllegalArgumentException("A ref ConfigurationOption must not have a blank ref");
      }
      if (name != null
          || declarativeName != null
          || description != null
          || defaultValue != null
          || type != null
          || examples != null
          || declarativeType != null
          || declarativeSchema != null
          || id != null) {
        throw new IllegalArgumentException(
            "A ref ConfigurationOption must not specify any other fields; it carries only the ref"
                + " id");
      }
    } else {
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
  }

  public ConfigurationOption(
      String name, String description, String defaultValue, ConfigurationType type) {
    this(name, null, description, defaultValue, type, null, null, null, null, null);
  }

  /** Returns a copy of this option with the given definition id assigned. */
  public ConfigurationOption withId(String id) {
    return new ConfigurationOption(
        name,
        declarativeName,
        description,
        defaultValue,
        type,
        examples,
        declarativeType,
        declarativeSchema,
        ref,
        id);
  }
}
