/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.JedisPipeline;

public final class JedisPipelineContext {
  private static final ThreadLocal<JedisPipeline> currentPipeline = new ThreadLocal<>();
  private static final VirtualField<JedisPipeline, CapturedRequests> requestsByPipeline =
      VirtualField.find(JedisPipeline.class, CapturedRequests.class);

  public static void enter(Object pipeline) {
    currentPipeline.set((JedisPipeline) pipeline);
  }

  public static void exit() {
    currentPipeline.remove();
  }

  public static boolean capture(Object request) {
    JedisPipeline pipeline = currentPipeline.get();
    if (pipeline == null) {
      return false;
    }
    CapturedRequests requests = requestsByPipeline.get(pipeline);
    if (requests == null) {
      requests = new CapturedRequests();
      requestsByPipeline.set(pipeline, requests);
    }
    requests.add(request);
    return true;
  }

  public static List<Object> getAndClearCurrentPipelineRequests() {
    JedisPipeline pipeline = currentPipeline.get();
    if (pipeline == null) {
      return emptyList();
    }
    CapturedRequests requests = requestsByPipeline.get(pipeline);
    requestsByPipeline.set(pipeline, null);
    return requests != null ? requests.requests() : emptyList();
  }

  private static class CapturedRequests {
    private final List<Object> requests = new ArrayList<>();

    private void add(Object request) {
      requests.add(request);
    }

    private List<Object> requests() {
      return requests;
    }
  }

  private JedisPipelineContext() {}
}
