/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.instrumentation.testing.util.ContextStorageCloser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/** Closes the API bridge storage while retaining the agent's strict-context leak checks. */
public final class AgentContextStorageCloser {

  public static void close() throws Exception {
    ContextStorage storage = ContextStorage.get();
    if (storage.getClass().getName().contains("StrictContextStorage")) {
      ContextStorageCloser.close(storage);
      return;
    }

    Object agentStorage = getAgentContextStorage();
    if (agentStorage != null
        && agentStorage.getClass().getName().contains("StrictContextStressor")) {
      Field field = agentStorage.getClass().getDeclaredField("contextStorage");
      field.setAccessible(true);
      agentStorage = field.get(agentStorage);
    }
    ContextStorageCloser.close(storage, agentStorage);
  }

  @Nullable
  private static Object getAgentContextStorage() {
    try {
      Class<?> contextStorageClass =
          Class.forName(
              "io.opentelemetry.javaagent.shaded.io.opentelemetry.context.ContextStorage");
      Method method = contextStorageClass.getDeclaredMethod("get");
      return method.invoke(null);
    } catch (Exception ignored) {
      return null;
    }
  }

  private AgentContextStorageCloser() {}
}
