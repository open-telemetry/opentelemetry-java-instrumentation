/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenAiErrorTypeTest {

  @Test
  void nullError() {
    assertThat(OpenAiErrorType.getErrorType(null)).isNull();
  }

  @Test
  void ordinaryException() {
    Throwable error = new IllegalStateException();

    assertThat(OpenAiErrorType.getErrorType(error))
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void serviceErrorCode() {
    Throwable error = new TestServiceException(Optional.of(" provider_code "), false);

    assertThat(OpenAiErrorType.getErrorType(error)).isEqualTo(" provider_code ");
  }

  @Test
  void missingServiceErrorCode() {
    Throwable error = new TestServiceException(Optional.empty(), false);

    assertThat(OpenAiErrorType.getErrorType(error)).isEqualTo(TestServiceException.class.getName());
  }

  @Test
  void malformedServiceErrorCode() {
    Throwable error = new TestServiceException(Optional.empty(), true);

    assertThat(OpenAiErrorType.getErrorType(error)).isEqualTo(TestServiceException.class.getName());
  }

  private static class TestServiceException extends OpenAIServiceException {

    private final Optional<String> code;
    private final boolean malformedCode;

    TestServiceException(Optional<String> code, boolean malformedCode) {
      super("test", null);
      this.code = code;
      this.malformedCode = malformedCode;
    }

    @Override
    public int statusCode() {
      return 400;
    }

    @Override
    public Headers headers() {
      return Headers.builder().build();
    }

    @Override
    public JsonValue body() {
      return JsonValue.from("{}");
    }

    @Override
    public Optional<String> code() {
      if (malformedCode) {
        throw new OpenAIInvalidDataException("malformed code");
      }
      return code;
    }

    @Override
    public Optional<String> param() {
      return Optional.empty();
    }

    @Override
    public Optional<String> type() {
      return Optional.empty();
    }
  }
}
