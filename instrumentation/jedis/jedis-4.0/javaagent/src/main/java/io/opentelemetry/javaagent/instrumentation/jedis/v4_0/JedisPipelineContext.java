/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

public final class JedisPipelineContext {
  // Pipeline and Transaction have no common supertype across the supported jedis range (Queable was
  // removed in 5.0), so captured requests are keyed on each concrete type separately.
  private static final ThreadLocal<Object> currentBatch = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> inTransactionFraming = new ThreadLocal<>();
  private static final VirtualField<Pipeline, CapturedRequests> PIPELINE_REQUESTS =
      VirtualField.find(Pipeline.class, CapturedRequests.class);
  private static final VirtualField<Transaction, CapturedRequests> TRANSACTION_REQUESTS =
      VirtualField.find(Transaction.class, CapturedRequests.class);

  public static void enter(Object batch) {
    // Pipeline aggregates at sync() and Transaction at exec(); both capture their queued commands
    // here. Other batch subtypes have no flush point, so leaving them uncaptured keeps their
    // per-command spans.
    if (batch instanceof Pipeline || batch instanceof Transaction) {
      currentBatch.set(batch);
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
    CapturedRequests requests = getOrCreateCapturedRequests(currentBatch.get());
    if (requests == null) {
      return false;
    }
    requests.add(request);
    return true;
  }

  @Nullable
  private static CapturedRequests getOrCreateCapturedRequests(@Nullable Object batch) {
    if (batch instanceof Pipeline) {
      Pipeline pipeline = (Pipeline) batch;
      CapturedRequests requests = PIPELINE_REQUESTS.get(pipeline);
      if (requests == null) {
        requests = new CapturedRequests();
        PIPELINE_REQUESTS.set(pipeline, requests);
      }
      return requests;
    }
    if (batch instanceof Transaction) {
      Transaction transaction = (Transaction) batch;
      CapturedRequests requests = TRANSACTION_REQUESTS.get(transaction);
      if (requests == null) {
        requests = new CapturedRequests();
        TRANSACTION_REQUESTS.set(transaction, requests);
      }
      return requests;
    }
    return null;
  }

  public static List<JedisRequest> getAndClearCapturedRequests(Object batch) {
    if (batch instanceof Pipeline) {
      Pipeline pipeline = (Pipeline) batch;
      CapturedRequests requests = PIPELINE_REQUESTS.get(pipeline);
      PIPELINE_REQUESTS.set(pipeline, null);
      return requests != null ? requests.requests() : emptyList();
    }
    if (batch instanceof Transaction) {
      Transaction transaction = (Transaction) batch;
      CapturedRequests requests = TRANSACTION_REQUESTS.get(transaction);
      TRANSACTION_REQUESTS.set(transaction, null);
      return requests != null ? requests.requests() : emptyList();
    }
    return emptyList();
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
