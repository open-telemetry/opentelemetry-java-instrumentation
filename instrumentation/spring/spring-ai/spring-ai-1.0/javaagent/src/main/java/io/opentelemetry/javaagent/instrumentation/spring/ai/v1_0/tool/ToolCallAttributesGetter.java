package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool;

import io.opentelemetry.instrumentation.api.instrumenter.genai.tool.GenAiToolAttributesGetter;
import javax.annotation.Nullable;

public enum ToolCallAttributesGetter implements GenAiToolAttributesGetter<ToolCallRequest, String> {
  INSTANCE;

  @Override
  public String getOperationName(ToolCallRequest request) {
    return request.getOperationName();
  }

  @Override
  public String getOperationTarget(ToolCallRequest request) {
    return getToolName(request);
  }

  @Override
  public String getToolDescription(ToolCallRequest request) {
    return request.getDescription();
  }

  @Override
  public String getToolName(ToolCallRequest request) {
    return request.getName();
  }

  @Override
  public String getToolType(ToolCallRequest request) {
    return "function";
  }

  @Nullable
  @Override
  public String getToolCallArguments(ToolCallRequest request) {
    return request.getToolInput();
  }

  @Nullable
  @Override
  public String getToolCallId(ToolCallRequest request, String response) {
    return request.getToolCallId();
  }

  @Nullable
  @Override
  public String getToolCallResult(ToolCallRequest request, String response) {
    return response;
  }
}
