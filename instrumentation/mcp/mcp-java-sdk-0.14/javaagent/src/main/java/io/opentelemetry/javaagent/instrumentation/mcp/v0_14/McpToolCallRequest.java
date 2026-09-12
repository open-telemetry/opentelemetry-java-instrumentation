/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import javax.annotation.Nullable;

final class McpToolCallRequest {
  private final String toolName;
  @Nullable private volatile String requestId;

  McpToolCallRequest(String toolName) {
    this.toolName = toolName;
  }

  String getToolName() {
    return toolName;
  }

  @Nullable
  String getRequestId() {
    return requestId;
  }

  void setRequestId(String requestId) {
    this.requestId = requestId;
  }
}
