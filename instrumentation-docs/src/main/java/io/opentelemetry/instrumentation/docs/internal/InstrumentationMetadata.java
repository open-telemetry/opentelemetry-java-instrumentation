/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
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

  private List<String> classifications;

  private List<ConfigurationOption> configurations = emptyList();

  public InstrumentationMetadata() {
    this.classifications = singletonList(InstrumentationClassification.LIBRARY.name());
  }

  public InstrumentationMetadata(
      @Nullable String description,
      @Nullable List<String> classifications,
      @Nullable Boolean disabledByDefault,
      @Nullable List<ConfigurationOption> configurations) {
    this.classifications =
        Objects.requireNonNullElse(
            classifications, singletonList(InstrumentationClassification.LIBRARY.name()));
    this.disabledByDefault = disabledByDefault;
    this.description = description;
    this.configurations = Objects.requireNonNullElse(configurations, emptyList());
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public List<InstrumentationClassification> getClassifications() {
    if (classifications == null || classifications.isEmpty()) {
      return singletonList(InstrumentationClassification.LIBRARY);
    }
    return classifications.stream()
        .map(
            classification -> {
              InstrumentationClassification result =
                  InstrumentationClassification.fromString(classification);
              if (result == null) {
                throw new IllegalArgumentException("Invalid classification: " + classification);
              }
              return result;
            })
        .toList();
  }

  public Boolean getDisabledByDefault() {
    return Objects.requireNonNullElse(disabledByDefault, false);
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  public void setClassifications(@Nullable List<String> classifications) {
    this.classifications =
        (classifications == null || classifications.isEmpty())
            ? singletonList(InstrumentationClassification.LIBRARY.name())
            : classifications;
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
}
