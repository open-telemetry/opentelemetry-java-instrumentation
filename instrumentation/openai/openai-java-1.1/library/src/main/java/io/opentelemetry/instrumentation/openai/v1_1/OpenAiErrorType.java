/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import javax.annotation.Nullable;

final class OpenAiErrorType {

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    if (!(error instanceof OpenAIServiceException)) {
      return null;
    }

    try {
      return ((OpenAIServiceException) error).code().orElse(null);
    } catch (OpenAIInvalidDataException ignored) {
      // Typed SDK accessors can throw for an unexpected JSON field type.
      return null;
    }
  }

  private OpenAiErrorType() {}
}
