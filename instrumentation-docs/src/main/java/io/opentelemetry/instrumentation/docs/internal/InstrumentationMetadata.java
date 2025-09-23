/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the data in a metadata.yaml file. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public class InstrumentationMetadata {
  @Nullable private String description;

  @JsonProperty("disabled_by_default")
  @Nullable
  private Boolean disabledByDefault;

  private String classification;

  @JsonProperty("library_link")
  @Nullable
  private String libraryLink;

  @JsonProperty("display_name")
  @Nullable
  private String displayName;

  private List<ConfigurationOption> configurations = emptyList();

  @JsonProperty("semantic_conventions")
  private List<SemanticConvention> semanticConventions = emptyList();

  @JsonProperty("additional_telemetry")
  private List<ManualTelemetryEntry> additionalTelemetry = emptyList();

  @JsonProperty("override_telemetry")
  @Nullable
  private Boolean overrideTelemetry;

  public InstrumentationMetadata() {
    this.classification = InstrumentationClassification.LIBRARY.name();
  }

  public InstrumentationMetadata(
      @Nullable String description,
      @Nullable Boolean disabledByDefault,
      String classification,
      @Nullable String libraryLink,
      @Nullable String displayName,
      @Nullable List<SemanticConvention> semanticConventions,
      @Nullable List<ConfigurationOption> configurations) {
    this.classification = classification;
    this.disabledByDefault = disabledByDefault;
    this.description = description;
    this.libraryLink = libraryLink;
    this.displayName = displayName;
    this.semanticConventions = Objects.requireNonNullElse(semanticConventions, emptyList());
    this.configurations = Objects.requireNonNullElse(configurations, emptyList());
    this.overrideTelemetry = null;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public void setDisplayName(@Nullable String displayName) {
    this.displayName = displayName;
  }

  @Nullable
  public String getDisplayName() {
    return displayName;
  }

  @Nonnull
  public InstrumentationClassification getClassification() {
    return Objects.requireNonNullElse(
        InstrumentationClassification.fromString(classification),
        InstrumentationClassification.LIBRARY);
  }

  public Boolean getDisabledByDefault() {
    return Objects.requireNonNullElse(disabledByDefault, false);
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  public void setClassification(String classification) {
    this.classification = classification;
  }

  public void setDisabledByDefault(@Nullable Boolean disabledByDefault) {
    this.disabledByDefault = disabledByDefault;
  }

  public List<ConfigurationOption> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(@Nullable List<ConfigurationOption> configurations) {
    this.configurations = Objects.requireNonNullElse(configurations, emptyList());
  }

  public List<SemanticConvention> getSemanticConventions() {
    return semanticConventions;
  }

  public void setSemanticConventions(@Nullable List<SemanticConvention> semanticConventions) {
    this.semanticConventions = Objects.requireNonNullElse(semanticConventions, emptyList());
  }

  @Nullable
  public String getLibraryLink() {
    return libraryLink;
  }

  public void setLibraryLink(@Nullable String libraryLink) {
    this.libraryLink = libraryLink;
  }

  public List<ManualTelemetryEntry> getAdditionalTelemetry() {
    return additionalTelemetry;
  }

  public void setAdditionalTelemetry(@Nullable List<ManualTelemetryEntry> additionalTelemetry) {
    this.additionalTelemetry = Objects.requireNonNullElse(additionalTelemetry, emptyList());
  }

  public Boolean getOverrideTelemetry() {
    return Objects.requireNonNullElse(overrideTelemetry, false);
  }

  public void setOverrideTelemetry(@Nullable Boolean overrideTelemetry) {
    this.overrideTelemetry = overrideTelemetry;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Builder {

    @Nullable private String description;
    @Nullable private Boolean disabledByDefault;
    @Nullable private String classification;
    @Nullable private String libraryLink;
    @Nullable private String displayName;
    private List<ConfigurationOption> configurations = emptyList();
    private List<SemanticConvention> semanticConventions = emptyList();
    private List<ManualTelemetryEntry> additionalTelemetry = emptyList();
    @Nullable private Boolean overrideTelemetry;

    @CanIgnoreReturnValue
    public Builder description(@Nullable String description) {
      this.description = description;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder disabledByDefault(@Nullable Boolean disabledByDefault) {
      this.disabledByDefault = disabledByDefault;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder classification(@Nullable String classification) {
      this.classification = classification;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder libraryLink(@Nullable String libraryLink) {
      this.libraryLink = libraryLink;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder displayName(@Nullable String displayName) {
      this.displayName = displayName;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder configurations(@Nullable List<ConfigurationOption> configurations) {
      this.configurations = Objects.requireNonNullElse(configurations, emptyList());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder semanticConventions(@Nullable List<SemanticConvention> semanticConventions) {
      this.semanticConventions = Objects.requireNonNullElse(semanticConventions, emptyList());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder additionalTelemetry(@Nullable List<ManualTelemetryEntry> additionalTelemetry) {
      this.additionalTelemetry = Objects.requireNonNullElse(additionalTelemetry, emptyList());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder overrideTelemetry(@Nullable Boolean overrideTelemetry) {
      this.overrideTelemetry = overrideTelemetry;
      return this;
    }

    public InstrumentationMetadata build() {
      InstrumentationMetadata metadata =
          new InstrumentationMetadata(
              description,
              disabledByDefault,
              classification != null
                  ? classification
                  : InstrumentationClassification.LIBRARY.name(),
              libraryLink,
              displayName,
              semanticConventions,
              configurations);
      metadata.setAdditionalTelemetry(additionalTelemetry);
      metadata.setOverrideTelemetry(overrideTelemetry);
      return metadata;
    }
  }
}
