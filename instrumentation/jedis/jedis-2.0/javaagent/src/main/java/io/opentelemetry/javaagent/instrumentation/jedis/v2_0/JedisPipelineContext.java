/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Queable;
import redis.clients.jedis.Transaction;

public final class JedisPipelineContext {
  private static final ThreadLocal<Queable> currentBatch = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> inTransactionFraming = new ThreadLocal<>();
  private static final VirtualField<Queable, CapturedRequests> CAPTURED_REQUESTS =
      VirtualField.find(Queable.class, CapturedRequests.class);

  public static void enter(Object batch) {
    // Pipeline aggregates at sync() and Transaction at exec(); both capture their queued commands
    // here. Other Queable subtypes have no flush point, so leaving them uncaptured keeps their
    // per-command spans.
    if (batch instanceof Pipeline || batch instanceof Transaction) {
      currentBatch.set((Queable) batch);
    }
  }

  public static void exit() {
    currentBatch.remove();
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
    Queable batch = currentBatch.get();
    if (batch == null) {
      return false;
    }
    CapturedRequests requests = CAPTURED_REQUESTS.get(batch);
    if (requests == null) {
      requests = new CapturedRequests();
      CAPTURED_REQUESTS.set(batch, requests);
    }
    requests.add(request);
    return true;
  }

  public static List<JedisRequest> getAndClearCapturedRequests(Object batch) {
    CapturedRequests requests = CAPTURED_REQUESTS.get((Queable) batch);
    CAPTURED_REQUESTS.set((Queable) batch, null);
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
