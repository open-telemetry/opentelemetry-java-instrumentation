/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InstrumentationMetaData {

  public InstrumentationMetaData() {}

  public InstrumentationMetaData(String description) {
    this.description = description;
    this.isLibraryInstrumentation = true;
  }

  public InstrumentationMetaData(String description, Boolean isLibraryInstrumentation) {
    this.isLibraryInstrumentation = isLibraryInstrumentation;
    this.description = description;
  }

  @Nullable private String description;

  private Boolean isLibraryInstrumentation;

  @Nullable
  public String getDescription() {
    return description;
  }

  public Boolean getIsLibraryInstrumentation() {
    if (isLibraryInstrumentation == null) {
      return true;
    }
    return isLibraryInstrumentation;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setIsLibraryInstrumentation(Boolean libraryInstrumentation) {
    isLibraryInstrumentation = libraryInstrumentation;
  }
}
