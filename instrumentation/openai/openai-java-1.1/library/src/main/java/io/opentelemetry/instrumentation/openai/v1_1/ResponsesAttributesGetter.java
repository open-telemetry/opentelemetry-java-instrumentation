/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseUsage;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesGetter;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

// Fields are read through their JsonField accessors: the typed accessors throw when a required
// field is missing and some changed signature across openai-java versions (for example,
// ResponseCreateParams.model() became Optional).
final class ResponsesAttributesGetter
    implements GenAiAttributesGetter<ResponseCreateRequest, Response> {

  ResponsesAttributesGetter() {}

  @Override
  public String getOperationName(ResponseCreateRequest request) {
    return GenAiAttributes.GenAiOperationNameIncubatingValues.CHAT;
  }

  @Override
  public String getSystem(ResponseCreateRequest request) {
    return GenAiAttributes.GenAiProviderNameIncubatingValues.OPENAI;
  }

  @Nullable
  @Override
  public String getErrorType(
      ResponseCreateRequest request, @Nullable Response response, @Nullable Throwable error) {
    return OpenAiErrorType.getErrorType(error);
  }

  @Nullable
  @Override
  public String getRequestModel(ResponseCreateRequest request) {
    return request
        .getRequest()
        ._model()
        .asKnown()
        .map(ResponsesAttributesGetter::modelName)
        .orElse(null);
  }

  @Override
  public boolean isRequestStreaming(ResponseCreateRequest request) {
    return request.isStreaming();
  }

  @Nullable
  @Override
  public Long getRequestSeed(ResponseCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getRequestEncodingFormats(ResponseCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestFrequencyPenalty(ResponseCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getRequestMaxTokens(ResponseCreateRequest request) {
    return request.getRequest()._maxOutputTokens().asKnown().orElse(null);
  }

  @Nullable
  @Override
  public Double getRequestPresencePenalty(ResponseCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getRequestStopSequences(ResponseCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTemperature(ResponseCreateRequest request) {
    return request.getRequest()._temperature().asKnown().orElse(null);
  }

  @Nullable
  @Override
  public Double getRequestTopK(ResponseCreateRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Double getRequestTopP(ResponseCreateRequest request) {
    return request.getRequest()._topP().asKnown().orElse(null);
  }

  @Override
  public List<String> getResponseFinishReasons(
      ResponseCreateRequest request, @Nullable Response response) {
    if (response == null) {
      return emptyList();
    }
    String finishReason = finishReason(response);
    return finishReason == null ? emptyList() : singletonList(finishReason);
  }

  @Override
  @Nullable
  public String getResponseId(ResponseCreateRequest request, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    return response._id().asKnown().orElse(null);
  }

  @Override
  @Nullable
  public String getResponseModel(ResponseCreateRequest request, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    return response._model().asKnown().map(ResponsesAttributesGetter::modelName).orElse(null);
  }

  @Override
  @Nullable
  public Long getUsageInputTokens(ResponseCreateRequest request, @Nullable Response response) {
    return usage(response).flatMap(usage -> usage._inputTokens().asKnown()).orElse(null);
  }

  @Override
  @Nullable
  public Long getUsageCacheReadInputTokens(
      ResponseCreateRequest request, @Nullable Response response) {
    return usage(response)
        .flatMap(usage -> usage._inputTokensDetails().asKnown())
        .flatMap(details -> details._cachedTokens().asKnown())
        .orElse(null);
  }

  @Override
  @Nullable
  public Long getUsageOutputTokens(ResponseCreateRequest request, @Nullable Response response) {
    return usage(response).flatMap(usage -> usage._outputTokens().asKnown()).orElse(null);
  }

  @Override
  @Nullable
  public Long getUsageReasoningOutputTokens(
      ResponseCreateRequest request, @Nullable Response response) {
    return usage(response)
        .flatMap(usage -> usage._outputTokensDetails().asKnown())
        .flatMap(details -> details._reasoningTokens().asKnown())
        .orElse(null);
  }

  private static Optional<ResponseUsage> usage(@Nullable Response response) {
    if (response == null) {
      return Optional.empty();
    }
    return response._usage().asKnown();
  }

  @Nullable
  private static String modelName(ResponsesModel model) {
    if (model.isString()) {
      return model.asString();
    }
    if (model.isChat()) {
      return model.asChat().asString();
    }
    if (model.isOnly()) {
      return model.asOnly().asString();
    }
    return null;
  }

  static boolean isFailed(Response response) {
    return "failed".equals(status(response));
  }

  // The Responses API reports a status instead of per-choice finish reasons. This maps it the
  // same way as the Python OpenAI instrumentation: a completed response stopped, a failed or
  // cancelled one errored, and an incomplete one reports why it stopped early.
  @Nullable
  private static String finishReason(Response response) {
    String status = status(response);
    if (status == null) {
      return null;
    }
    switch (status) {
      case "completed":
        return "stop";
      case "failed":
      case "cancelled":
        return "error";
      case "incomplete":
        String reason =
            response
                ._incompleteDetails()
                .asKnown()
                .flatMap(details -> details._reason().asKnown())
                .map(r -> r.asString())
                .orElse(null);
        if (reason == null) {
          return status;
        }
        if (reason.equals("max_output_tokens")) {
          return "length";
        }
        return reason;
      default:
        return null;
    }
  }

  @Nullable
  private static String status(Response response) {
    return response._status().asKnown().map(s -> s.asString()).orElse(null);
  }
}
