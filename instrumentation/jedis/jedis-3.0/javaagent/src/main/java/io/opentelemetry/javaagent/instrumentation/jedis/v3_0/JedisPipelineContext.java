/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisSingletons.currentPipeline;
import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisSingletons.currentTransactionFraming;
import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.PipelineBase;

public final class JedisPipelineContext {
  private static final VirtualField<PipelineBase, List<JedisRequest>> CAPTURED_REQUESTS =
      VirtualField.find(PipelineBase.class, List.class);

  public static boolean inTransactionFraming() {
    return Boolean.TRUE.equals(currentTransactionFraming().get());
  }

  public static boolean capture(JedisRequest request) {
    PipelineBase pipeline = currentPipeline().get();
    if (pipeline == null) {
      return false;
    }
    List<JedisRequest> requests = CAPTURED_REQUESTS.get(pipeline);
    if (requests == null) {
      requests = new ArrayList<>();
      CAPTURED_REQUESTS.set(pipeline, requests);
    }
    requests.add(request);
    return true;
  }

  public static List<JedisRequest> getAndClearCapturedRequests(PipelineBase pipeline) {
    List<JedisRequest> requests = CAPTURED_REQUESTS.get(pipeline);
    CAPTURED_REQUESTS.set(pipeline, null);
    return requests != null ? requests : emptyList();
  }

  private JedisPipelineContext() {}
}
