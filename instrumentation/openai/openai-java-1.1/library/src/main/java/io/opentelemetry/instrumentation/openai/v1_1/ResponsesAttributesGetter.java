/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.lang.invoke.MethodType.methodType;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.openai.core.JsonValue;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseUsage;
import io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

// Fields are read through their JsonField accessors: the typed accessors throw when a required
// field is missing and some changed signature across openai-java versions (for example,
// ResponseCreateParams.model() became Optional).
final class ResponsesAttributesGetter
    implements GenAiAttributesGetter<ResponseCreateRequest, Response> {

  private static final Set<String> NON_ITEM_STATUSES =
      new HashSet<>(asList("failed", "cancelled", "incomplete", "queued", "in_progress"));
  private static final Set<String> FINISHED_TOOL_CALL_STATUSES =
      new HashSet<>(asList("completed", "incomplete"));

  // Custom tool calls were added in SDK 3.x. Their status isn't a declared field, so it's read from
  // the call's additional properties.
  @Nullable private static final MethodHandle IS_CUSTOM_TOOL_CALL_HANDLE;
  @Nullable private static final MethodHandle CUSTOM_TOOL_CALL_STATUS_HANDLE;

  static {
    MethodHandle isCustomToolCall;
    MethodHandle customToolCallStatus;
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      Class<?> customToolCallClass =
          Class.forName(
              "com.openai.models.responses.ResponseCustomToolCall",
              false,
              ResponsesAttributesGetter.class.getClassLoader());
      isCustomToolCall =
          lookup.findVirtual(
              ResponseOutputItem.class, "isCustomToolCall", methodType(boolean.class));
      MethodHandle asCustomToolCall =
          lookup.findVirtual(
              ResponseOutputItem.class, "asCustomToolCall", methodType(customToolCallClass));
      MethodHandle additionalProperties =
          lookup.findVirtual(customToolCallClass, "_additionalProperties", methodType(Map.class));
      MethodHandle getStatus =
          MethodHandles.insertArguments(
              lookup.findVirtual(Map.class, "get", methodType(Object.class, Object.class)),
              1,
              "status");
      customToolCallStatus =
          MethodHandles.filterReturnValue(
              MethodHandles.filterReturnValue(asCustomToolCall, additionalProperties), getStatus);
    } catch (Exception ignored) {
      isCustomToolCall = null;
      customToolCallStatus = null;
    }
    IS_CUSTOM_TOOL_CALL_HANDLE = isCustomToolCall;
    CUSTOM_TOOL_CALL_STATUS_HANDLE = customToolCallStatus;
  }

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
    String status = status(response);
    // Like the Python OpenAI instrumentation, a response that hasn't failed, been cancelled, cut
    // short or left pending reports the distinct finish reasons of its output items in order, so
    // a response that ends in tool calls reports tool_calls.
    if (status == null || !NON_ITEM_STATUSES.contains(status)) {
      Set<String> finishReasons = new LinkedHashSet<>();
      for (ResponseOutputItem item : response._output().asKnown().orElse(emptyList())) {
        if (isFinishedToolCall(item)) {
          finishReasons.add("tool_calls");
        } else if (item.isMessage()) {
          String reason =
              finishReason(
                  item.asMessage()._status().asKnown().map(s -> s.asString()).orElse(null), null);
          if (reason != null) {
            finishReasons.add(reason);
          }
        }
      }
      if (!finishReasons.isEmpty()) {
        return new ArrayList<>(finishReasons);
      }
    }
    String finishReason = finishReason(status, incompleteReason(response));
    return finishReason == null ? emptyList() : singletonList(finishReason);
  }

  // A tool call without a status counts as finished, since custom tool calls don't declare one.
  private static boolean isFinishedToolCall(ResponseOutputItem item) {
    if (item.isFunctionCall()) {
      String status = item.asFunctionCall()._status().asKnown().map(s -> s.asString()).orElse(null);
      return status == null || FINISHED_TOOL_CALL_STATUSES.contains(status);
    }
    if (IS_CUSTOM_TOOL_CALL_HANDLE == null || CUSTOM_TOOL_CALL_STATUS_HANDLE == null) {
      return false;
    }
    try {
      if (!(boolean) IS_CUSTOM_TOOL_CALL_HANDLE.invoke(item)) {
        return false;
      }
      JsonValue status = (JsonValue) CUSTOM_TOOL_CALL_STATUS_HANDLE.invoke(item);
      return status == null
          || status.equals(JsonValue.from(null))
          || status.equals(JsonValue.from("completed"))
          || status.equals(JsonValue.from("incomplete"));
    } catch (Throwable t) {
      return false;
    }
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

  // A failed response and a typed error stream event arrive as normal results, without an
  // exception for the error type to fall back to, so report the error code they carry, like the
  // Python OpenAI instrumentation does.
  @Nullable
  @Override
  public String getErrorType(
      ResponseCreateRequest request, @Nullable Response response, @Nullable Throwable error) {
    if (response != null) {
      String code =
          response
              ._error()
              .asKnown()
              .flatMap(responseError -> responseError._code().asKnown())
              .map(c -> c.asString())
              .orElse(null);
      if (code != null) {
        return code;
      }
    }
    return request.getStreamErrorCode();
  }

  static boolean isFailed(Response response) {
    return "failed".equals(status(response));
  }

  // Maps a response or output item status the same way as the Python OpenAI instrumentation: a
  // completed one stopped, a failed or cancelled one errored, and an incomplete one reports why it
  // stopped early.
  @Nullable
  private static String finishReason(@Nullable String status, @Nullable String incompleteReason) {
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
        if (incompleteReason == null) {
          return status;
        }
        if (incompleteReason.equals("max_output_tokens")) {
          return "length";
        }
        return incompleteReason;
      default:
        return null;
    }
  }

  @Nullable
  private static String incompleteReason(Response response) {
    return response
        ._incompleteDetails()
        .asKnown()
        .flatMap(details -> details._reason().asKnown())
        .map(r -> r.asString())
        .orElse(null);
  }

  @Nullable
  private static String status(Response response) {
    return response._status().asKnown().map(s -> s.asString()).orElse(null);
  }
}
