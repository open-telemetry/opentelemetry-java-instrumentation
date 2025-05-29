/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import graphql.execution.ResultPath;
import io.opentelemetry.context.Context;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class Graphql20OpenTelemetryInstrumentationState
    extends io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationState {
  private static final String ROOT_PATH = ResultPath.rootPath().toString();

  private final ConcurrentMap<String, Context> contextStorage = new ConcurrentHashMap<>();

  @Override
  public Context getContext() {
    return contextStorage.getOrDefault(ROOT_PATH, Context.current());
  }

  @Override
  public void setContext(Context context) {
    this.contextStorage.put(ROOT_PATH, context);
  }

  public Context setContextForPath(ResultPath resultPath, Context context) {
    return contextStorage.putIfAbsent(resultPath.toString(), context);
  }

  public Context getParentContextForPath(ResultPath resultPath) {

    // Navigate up the path until we find the closest parent context
    for (ResultPath currentPath = resultPath.getParent();
        currentPath != null;
        currentPath = currentPath.getParent()) {

      Context parentContext = contextStorage.getOrDefault(currentPath.toString(), null);

      if (parentContext != null) {
        return parentContext;
      }
    }

    // Fallback to returning the context for ROOT_PATH
    return getContext();
  }
}
