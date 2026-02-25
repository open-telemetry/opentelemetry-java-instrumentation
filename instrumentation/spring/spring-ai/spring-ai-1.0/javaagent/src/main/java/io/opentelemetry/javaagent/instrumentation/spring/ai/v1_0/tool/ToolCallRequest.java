/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool;

import org.springframework.ai.tool.definition.ToolDefinition;

public final class ToolCallRequest {

  private final String toolInput;
  private final String toolCallId;
  private final ToolDefinition toolDefinition;

  private ToolCallRequest(String toolInput, String toolCallId, ToolDefinition toolDefinition) {
    this.toolInput = toolInput;
    this.toolCallId = toolCallId;
    this.toolDefinition = toolDefinition;
  }

  public static ToolCallRequest create(
      String toolInput, String toolCallId, ToolDefinition toolDefinition) {
    return new ToolCallRequest(toolInput, toolCallId, toolDefinition);
  }

  public String getOperationName() {
    return "execute_tool";
  }

  public String getType() {
    // spring ai support function only
    return "function";
  }

  public String getName() {
    if (toolDefinition == null) {
      return null;
    }
    return toolDefinition.name();
  }

  public String getDescription() {
    if (toolDefinition == null) {
      return null;
    }
    return toolDefinition.description();
  }

  public String getToolInput() {
    return toolInput;
  }

  public String getToolCallId() {
    return toolCallId;
  }
}
