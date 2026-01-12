/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.errorprone.bugpatterns.checkreturnvalue;

import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.bugpatterns.WellKnownKeep;

public final class CanIgnoreReturnValueSuggesterFactory {

  // calls package private constructor of CanIgnoreReturnValueSuggester
  public static CanIgnoreReturnValueSuggester createCanIgnoreReturnValueSuggester(
      ErrorProneFlags errorProneFlags, WellKnownKeep wellKnownKeep) {
    return new CanIgnoreReturnValueSuggester(errorProneFlags, wellKnownKeep);
  }

  private CanIgnoreReturnValueSuggesterFactory() {}
}
