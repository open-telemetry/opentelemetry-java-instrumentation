/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.errorprone.bugpatterns.checkreturnvalue;

import com.google.errorprone.ErrorProneFlags;

public final class CanIgnoreReturnValueSuggesterFactory {

  // calls package private constructor of CanIgnoreReturnValueSuggester
  public static CanIgnoreReturnValueSuggester createCanIgnoreReturnValueSuggester(
      ErrorProneFlags errorProneFlags) {
    return new CanIgnoreReturnValueSuggester(errorProneFlags);
  }

  private CanIgnoreReturnValueSuggesterFactory() {}
}
