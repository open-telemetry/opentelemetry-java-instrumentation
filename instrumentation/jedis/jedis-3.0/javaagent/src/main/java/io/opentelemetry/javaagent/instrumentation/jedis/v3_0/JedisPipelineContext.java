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
import redis.clients.jedis.Transaction;

public final class JedisPipelineContext {
  private static final ThreadLocal<PipelineBase> currentPipeline = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> inTransactionFraming = new ThreadLocal<>();
  private static final VirtualField<PipelineBase, CapturedRequests> CAPTURED_REQUESTS =
      VirtualField.find(PipelineBase.class, CapturedRequests.class);

  public static void enter(PipelineBase pipeline) {
    // Pipeline aggregates at sync(), Transaction at exec(); both capture their queued commands
    // here.
    // Other MultiKeyPipelineBase subtypes have no flush point, so leaving them uncaptured keeps
    // their per-command spans.
    if (pipeline instanceof Pipeline || pipeline instanceof Transaction) {
      currentPipeline.set(pipeline);
    }
  }

  public static void exit() {
    currentPipeline.remove();
  }

  public static void enterTransactionFraming() {
    inTransactionFraming.set(Boolean.TRUE);
  }

  public static void exitTransactionFraming() {
    inTransactionFraming.remove();
  }

  public static boolean inTransactionFraming() {
    return Boolean.TRUE.equals(inTransactionFraming.get());
  }

  public static boolean capture(JedisRequest request) {
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

  public static List<JedisRequest> getAndClearCapturedRequests(PipelineBase pipeline) {
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
