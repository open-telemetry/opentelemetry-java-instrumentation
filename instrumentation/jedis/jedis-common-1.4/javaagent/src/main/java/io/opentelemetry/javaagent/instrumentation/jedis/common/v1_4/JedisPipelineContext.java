/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

public final class JedisPipelineContext {
  private static final ThreadLocal<Object> currentPipeline = new ThreadLocal<>();
  private static final Map<Object, List<Object>> requestsByPipeline =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static void enter(Object pipeline) {
    currentPipeline.set(pipeline);
  }

  public static void exit() {
    currentPipeline.remove();
  }

  public static boolean capture(Object request) {
    Object pipeline = currentPipeline.get();
    if (pipeline == null) {
      return false;
    }
    requestsByPipeline.computeIfAbsent(pipeline, unused -> new ArrayList<>()).add(request);
    return true;
  }

  public static List<Object> drain(Object pipeline) {
    List<Object> requests = requestsByPipeline.remove(pipeline);
    return requests != null ? requests : emptyList();
  }

  @Nullable
  public static Object currentPipeline() {
    return currentPipeline.get();
  }

  private JedisPipelineContext() {}
}
