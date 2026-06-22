/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.PipelineBase;

public final class JedisPipelineContext {
  private static final ThreadLocal<PipelineBase> currentPipeline = new ThreadLocal<>();
  private static final VirtualField<PipelineBase, CapturedRequests> CAPTURED_REQUESTS =
      VirtualField.find(PipelineBase.class, CapturedRequests.class);

  public static void enter(Object pipeline) {
    // Only aggregate real pipelines. Transaction also extends MultiKeyPipelineBase but completes
    // via exec()/discard() (not sync()), so its captured commands would never be flushed into a
    // batch span; leaving them uncaptured keeps the per-command spans.
    if (pipeline instanceof Pipeline) {
      currentPipeline.set((PipelineBase) pipeline);
    }
  }

  public static void exit() {
    currentPipeline.remove();
  }

  public static boolean capture(Object request) {
    PipelineBase pipeline = currentPipeline.get();
    if (pipeline == null) {
      return false;
    }
    CapturedRequests requests = CAPTURED_REQUESTS.get(pipeline);
    if (requests == null) {
      requests = new CapturedRequests();
      CAPTURED_REQUESTS.set(pipeline, requests);
    }
    requests.add(request);
    return true;
  }

  public static List<Object> getAndClearCapturedRequests(Object pipeline) {
    CapturedRequests requests = CAPTURED_REQUESTS.get((PipelineBase) pipeline);
    CAPTURED_REQUESTS.set((PipelineBase) pipeline, null);
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
