/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Queable;
import redis.clients.jedis.Transaction;

public final class JedisPipelineContext {
  private static final ThreadLocal<Queable> currentBatch = new ThreadLocal<>();
  private static final ThreadLocal<TransactionFraming> currentTransactionFraming =
      new ThreadLocal<>();
  private static final VirtualField<Queable, BatchState> BATCH_STATE =
      VirtualField.find(Queable.class, BatchState.class);

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
    currentTransactionFraming.set(new TransactionFraming(null));
  }

  public static void enterTransactionFraming(JedisRequest request) {
    currentTransactionFraming.set(new TransactionFraming(request));
  }

  public static void exitTransactionFraming(@Nullable Object transaction) {
    try {
      TransactionFraming framing = currentTransactionFraming.get();
      if (framing != null && framing.framingRequest != null && transaction instanceof Queable) {
        batchState((Queable) transaction).transactionFramingRequest = framing.framingRequest;
      }
    } finally {
      clearTransactionFraming();
    }
  }

  public static void exitTransactionFraming() {
    clearTransactionFraming();
  }

  private static void clearTransactionFraming() {
    currentTransactionFraming.remove();
  }

  public static boolean inTransactionFraming() {
    return currentTransactionFraming.get() != null;
  }

  public static void captureTransactionFramingPeer(JedisRequest request) {
    TransactionFraming framing = currentTransactionFraming.get();
    if (framing == null) {
      return;
    }
    if (framing.transactionRequest != null) {
      framing.transactionRequest.useLaterPeerAddress(request);
    } else {
      framing.framingRequest = request;
    }
  }

  @Nullable
  public static JedisRequest getAndClearTransactionFramingRequest(Object transaction) {
    if (!(transaction instanceof Queable)) {
      return null;
    }
    Queable queable = (Queable) transaction;
    BatchState state = BATCH_STATE.get(queable);
    if (state == null) {
      return null;
    }
    JedisRequest request = state.transactionFramingRequest;
    state.transactionFramingRequest = null;
    clearIfEmpty(queable, state);
    return request;
  }

  public static boolean capture(JedisRequest request) {
    Queable batch = currentBatch.get();
    if (batch == null) {
      return false;
    }
    batchState(batch).requests.add(request);
    return true;
  }

  public static List<JedisRequest> getAndClearCapturedRequests(Object batch) {
    if (!(batch instanceof Queable)) {
      return emptyList();
    }
    Queable queable = (Queable) batch;
    BatchState state = BATCH_STATE.get(queable);
    if (state == null) {
      return emptyList();
    }
    List<JedisRequest> requests = state.requests;
    state.requests = new ArrayList<>();
    clearIfEmpty(queable, state);
    return requests;
  }

  public static void clear(Object batch) {
    if (batch instanceof Queable) {
      BATCH_STATE.set((Queable) batch, null);
    }
  }

  private static BatchState batchState(Queable batch) {
    BatchState state = BATCH_STATE.get(batch);
    if (state == null) {
      state = new BatchState();
      BATCH_STATE.set(batch, state);
    }
    return state;
  }

  private static void clearIfEmpty(Queable batch, BatchState state) {
    if (state.requests.isEmpty() && state.transactionFramingRequest == null) {
      BATCH_STATE.set(batch, null);
    }
  }

  private JedisPipelineContext() {}

  private static final class TransactionFraming {
    @Nullable private final JedisRequest transactionRequest;
    @Nullable private JedisRequest framingRequest;

    private TransactionFraming(@Nullable JedisRequest transactionRequest) {
      this.transactionRequest = transactionRequest;
    }
  }

  private static final class BatchState {
    private List<JedisRequest> requests = new ArrayList<>();
    @Nullable private JedisRequest transactionFramingRequest;
  }
}
