/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.Pipeline;

public final class JedisPipelineContext {
  private static final ThreadLocal<Pipeline> currentPipeline = new ThreadLocal<>();
  private static final VirtualField<Pipeline, CapturedRequests> CAPTURED_REQUESTS =
      VirtualField.find(Pipeline.class, CapturedRequests.class);

  public static void enter(Pipeline pipeline) {
    currentPipeline.set(pipeline);
  }

  public static void exit() {
    currentPipeline.remove();
  }

  public static boolean capture(JedisRequest request) {
    Pipeline pipeline = currentPipeline.get();
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

  public static List<JedisRequest> getAndClearCapturedRequests(Pipeline pipeline) {
    CapturedRequests requests = CAPTURED_REQUESTS.get(pipeline);
    CAPTURED_REQUESTS.set(pipeline, null);
    return requests != null ? requests.requests() : emptyList();
  }

  private static class CapturedRequests {
    private final List<JedisRequest> requests = new ArrayList<>();

    private void add(JedisRequest request) {
      requests.add(request);
    }

    private List<JedisRequest> requests() {
      return requests;
    }
  }

  private JedisPipelineContext() {}
}
