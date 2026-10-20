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

    String code;
    try {
      code = ((OpenAIServiceException) error).code().orElse("");
    } catch (OpenAIInvalidDataException ignored) {
      // Typed SDK accessors can throw for an unexpected JSON field type.
      return null;
    }

    if (code.codePoints().allMatch(Character::isWhitespace)) {
      return null;
    }
    return code;
  }

  private OpenAiErrorType() {}
}
