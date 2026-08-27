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
  private static final ThreadLocal<Boolean> inTransactionFraming = new ThreadLocal<>();
  private static final ThreadLocal<JedisRequest> currentTransactionRequest = new ThreadLocal<>();
  private static final ThreadLocal<JedisRequest> currentTransactionFramingRequest =
      new ThreadLocal<>();
  private static final VirtualField<Queable, List<JedisRequest>> CAPTURED_REQUESTS =
      VirtualField.find(Queable.class, List.class);
  private static final VirtualField<Queable, JedisRequest> TRANSACTION_FRAMING_REQUEST =
      VirtualField.find(Queable.class, JedisRequest.class);

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

  public static void enterTransactionFraming(JedisRequest request) {
    enterTransactionFraming();
    currentTransactionRequest.set(request);
  }

  public static void exitTransactionFraming(@Nullable Object transaction) {
    try {
      JedisRequest request = currentTransactionFramingRequest.get();
      if (request != null && transaction instanceof Queable) {
        TRANSACTION_FRAMING_REQUEST.set((Queable) transaction, request);
      }
    } finally {
      clearTransactionFraming();
    }
  }

  public static void exitTransactionFraming() {
    clearTransactionFraming();
  }

  private static void clearTransactionFraming() {
    inTransactionFraming.remove();
    currentTransactionRequest.remove();
    currentTransactionFramingRequest.remove();
  }

  public static boolean inTransactionFraming() {
    return Boolean.TRUE.equals(inTransactionFraming.get());
  }

  public static void captureTransactionFramingPeer(JedisRequest request) {
    JedisRequest transactionRequest = currentTransactionRequest.get();
    if (transactionRequest != null) {
      transactionRequest.retainCommonPeerAddress(request);
    } else if (inTransactionFraming()) {
      currentTransactionFramingRequest.set(request);
    }
  }

  @Nullable
  public static JedisRequest getAndClearTransactionFramingRequest(Object transaction) {
    if (!(transaction instanceof Queable)) {
      return null;
    }
    Queable queable = (Queable) transaction;
    JedisRequest request = TRANSACTION_FRAMING_REQUEST.get(queable);
    TRANSACTION_FRAMING_REQUEST.set(queable, null);
    return request;
  }

  public static boolean capture(JedisRequest request) {
    Queable batch = currentBatch.get();
    if (batch == null) {
      return false;
    }
    List<JedisRequest> requests = CAPTURED_REQUESTS.get(batch);
    if (requests == null) {
      requests = new ArrayList<>();
      CAPTURED_REQUESTS.set(batch, requests);
    }
    requests.add(request);
    return true;
  }

  public static List<JedisRequest> getAndClearCapturedRequests(Object batch) {
    List<JedisRequest> requests = CAPTURED_REQUESTS.get((Queable) batch);
    CAPTURED_REQUESTS.set((Queable) batch, null);
    return requests != null ? requests : emptyList();
  }

  private JedisPipelineContext() {}
}
