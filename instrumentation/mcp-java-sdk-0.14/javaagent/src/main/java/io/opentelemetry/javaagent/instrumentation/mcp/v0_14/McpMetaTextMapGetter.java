/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;
import javax.annotation.Nullable;

final class McpMetaTextMapGetter implements TextMapGetter<Map<String, String>> {
  static final McpMetaTextMapGetter INSTANCE = new McpMetaTextMapGetter();

  @Override
  public Iterable<String> keys(Map<String, String> carrier) {
    return carrier.keySet();
  }

  @Override
  @Nullable
  public String get(@Nullable Map<String, String> carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.get(key);
  }

  private McpMetaTextMapGetter() {}
}
