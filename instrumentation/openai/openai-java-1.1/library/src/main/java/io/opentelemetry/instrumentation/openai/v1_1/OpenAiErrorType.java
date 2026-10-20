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

    // OpenAI-compatible services can return arbitrary strings. Only report known service codes.
    switch (code) {
      case "content_policy_violation":
      case "context_length_exceeded":
      case "insufficient_quota":
      case "invalid_api_key":
      case "invalid_value":
      case "model_not_found":
      case "rate_limit_exceeded":
      case "server_error":
        return code;
      default:
        return null;
    }
  }

  private OpenAiErrorType() {}
}
