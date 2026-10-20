/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.PipelineBase;
import redis.clients.jedis.Transaction;

public final class JedisPipelineContext {
  private static final ThreadLocal<PipelineBase> currentPipeline = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> inTransactionFraming = new ThreadLocal<>();
  private static final VirtualField<PipelineBase, List<JedisRequest>> CAPTURED_REQUESTS =
      VirtualField.find(PipelineBase.class, List.class);

  @Nullable
  public static PipelineBase enter(PipelineBase pipeline) {
    // Pipeline aggregates at sync(), Transaction at exec(); both capture their queued commands
    // here.
    // Other MultiKeyPipelineBase subtypes have no flush point, so leaving them uncaptured keeps
    // their per-command spans.
    if (pipeline instanceof Pipeline || pipeline instanceof Transaction) {
      PipelineBase previous = currentPipeline.get();
      currentPipeline.set(pipeline);
      return previous;
    }
    return currentPipeline.get();
  }

  public static void exit(@Nullable PipelineBase previous) {
    if (previous == null) {
      currentPipeline.remove();
    } else {
      currentPipeline.set(previous);
    }
  }

  @Nullable
  public static Boolean enterTransactionFraming() {
    Boolean previous = inTransactionFraming.get();
    inTransactionFraming.set(Boolean.TRUE);
    return previous;
  }

  public static void exitTransactionFraming(@Nullable Boolean previous) {
    if (previous == null) {
      inTransactionFraming.remove();
    } else {
      inTransactionFraming.set(previous);
    }
  }

  public static boolean inTransactionFraming() {
    return Boolean.TRUE.equals(inTransactionFraming.get());
  }

  public static boolean capture(JedisRequest request) {
    PipelineBase pipeline = currentPipeline.get();
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
