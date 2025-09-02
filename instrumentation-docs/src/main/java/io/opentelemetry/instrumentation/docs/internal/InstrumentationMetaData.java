/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the data in a metadata.yaml file. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public class InstrumentationMetaData {
  @Nullable private String description;

  @JsonProperty("disabled_by_default")
  @Nullable
  private Boolean disabledByDefault;

  private String classification;

  private List<ConfigurationOption> configurations = Collections.emptyList();

  public InstrumentationMetaData() {
    this.classification = InstrumentationClassification.LIBRARY.toString();
  }

  public InstrumentationMetaData(
      @Nullable String description,
      String classification,
      @Nullable Boolean disabledByDefault,
      @Nullable List<ConfigurationOption> configurations) {
    this.classification = classification;
    this.disabledByDefault = disabledByDefault;
    this.description = description;
    this.configurations = Objects.requireNonNullElse(configurations, Collections.emptyList());
  }

  @Nullable
  public String getDescription() {
    return description;
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
    this.configurations = Objects.requireNonNullElse(configurations, Collections.emptyList());
  }
}
