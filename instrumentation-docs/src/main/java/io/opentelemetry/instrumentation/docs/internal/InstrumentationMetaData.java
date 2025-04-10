/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the data in a metadata.yaml file. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public class InstrumentationMetaData {
  @Nullable private String description;
  @Nullable private Boolean disabledByDefault;
  private String classification;

  public InstrumentationMetaData() {}

  public InstrumentationMetaData(
      String description, String classification, Boolean disabledByDefault) {
    this.classification = classification;
    this.disabledByDefault = disabledByDefault;
    this.description = description;
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

  public void setClassification(@Nullable String classification) {
    this.classification = classification;
  }

  public void setDisabledByDefault(@Nullable Boolean disabledByDefault) {
    this.disabledByDefault = disabledByDefault;
  }
}
