/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents the data in a metadata.yaml file. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public class InstrumentationMetaData {
  @Nullable private String description;
  @Nullable private Boolean isLibraryInstrumentation;
  @Nullable private Boolean disabledByDefault;

  public InstrumentationMetaData() {}

  public InstrumentationMetaData(String description) {
    this.description = description;
  }

  public InstrumentationMetaData(
      String description, Boolean isLibraryInstrumentation, Boolean disabledByDefault) {
    this.isLibraryInstrumentation = isLibraryInstrumentation;
    this.disabledByDefault = disabledByDefault;
    this.description = description;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public Boolean getIsLibraryInstrumentation() {
    return Objects.requireNonNullElse(isLibraryInstrumentation, true);
  }

  public Boolean getDisabledByDefault() {
    return Objects.requireNonNullElse(disabledByDefault, false);
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  public void setIsLibraryInstrumentation(@Nullable Boolean libraryInstrumentation) {
    isLibraryInstrumentation = libraryInstrumentation;
  }

  public void setDisabledByDefault(@Nullable Boolean disabledByDefault) {
    this.disabledByDefault = disabledByDefault;
  }
}
