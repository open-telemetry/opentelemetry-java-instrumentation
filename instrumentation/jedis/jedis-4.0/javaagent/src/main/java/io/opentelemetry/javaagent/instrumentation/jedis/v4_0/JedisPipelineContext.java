/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.currentBatch;
import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.currentTransactionFraming;
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
  private static final VirtualField<Pipeline, List<JedisRequest>> PIPELINE_REQUESTS =
      VirtualField.find(Pipeline.class, List.class);
  private static final VirtualField<Transaction, List<JedisRequest>> TRANSACTION_REQUESTS =
      VirtualField.find(Transaction.class, List.class);

  public static boolean inTransactionFraming() {
    return Boolean.TRUE.equals(currentTransactionFraming().get());
  }

  public static boolean capture(JedisRequest request) {
    List<JedisRequest> requests = getOrCreateCapturedRequests(currentBatch().get());
    if (requests == null) {
      return false;
    }
    requests.add(request);
    return true;
  }

  @Nullable
  private static List<JedisRequest> getOrCreateCapturedRequests(@Nullable Object batch) {
    if (batch instanceof Pipeline) {
      Pipeline pipeline = (Pipeline) batch;
      List<JedisRequest> requests = PIPELINE_REQUESTS.get(pipeline);
      if (requests == null) {
        requests = new ArrayList<>();
        PIPELINE_REQUESTS.set(pipeline, requests);
      }
      return requests;
    }
    if (batch instanceof Transaction) {
      Transaction transaction = (Transaction) batch;
      List<JedisRequest> requests = TRANSACTION_REQUESTS.get(transaction);
      if (requests == null) {
        requests = new ArrayList<>();
        TRANSACTION_REQUESTS.set(transaction, requests);
      }
      return requests;
    }
    return null;
  }

  public static List<JedisRequest> getAndClearCapturedRequests(Object batch) {
    if (batch instanceof Pipeline) {
      Pipeline pipeline = (Pipeline) batch;
      List<JedisRequest> requests = PIPELINE_REQUESTS.get(pipeline);
      PIPELINE_REQUESTS.set(pipeline, null);
      return requests != null ? requests : emptyList();
    }
    if (batch instanceof Transaction) {
      Transaction transaction = (Transaction) batch;
      List<JedisRequest> requests = TRANSACTION_REQUESTS.get(transaction);
      TRANSACTION_REQUESTS.set(transaction, null);
      return requests != null ? requests : emptyList();
    }
    return emptyList();
  }

  private JedisPipelineContext() {}
}
