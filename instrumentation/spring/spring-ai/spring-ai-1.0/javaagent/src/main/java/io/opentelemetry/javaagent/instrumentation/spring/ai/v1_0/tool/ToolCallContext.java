/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.Map;

/**
 * Tool call context to store tool call ids map
 */
public final class ToolCallContext {

  private static final ContextKey<Map<String, String>> TOOL_CALL_IDS_KEY = 
      ContextKey.named("spring-ai-tool-call-ids");

  private ToolCallContext() {}

  public static Context storeToolCalls(Context context, Map<String, String> toolNameToIdMap) {
    if (toolNameToIdMap == null || toolNameToIdMap.isEmpty()) {
      return context;
    }
    return context.with(TOOL_CALL_IDS_KEY, toolNameToIdMap);
  }

  public static String getToolCallId(Context context, String toolName) {
    if (context == null || toolName == null) {
      return null;
    }
    
    Map<String, String> toolCallIds = context.get(TOOL_CALL_IDS_KEY);
    if (toolCallIds == null) {
      return null;
    }
    
    return toolCallIds.get(toolName);
  }
}
