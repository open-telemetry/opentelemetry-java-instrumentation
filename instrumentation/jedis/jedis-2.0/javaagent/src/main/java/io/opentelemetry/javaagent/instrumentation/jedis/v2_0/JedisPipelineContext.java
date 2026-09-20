/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.currentBatch;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.currentTransactionFraming;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.Queable;

public final class JedisPipelineContext {
  private static final VirtualField<Queable, BatchState> BATCH_STATE =
      VirtualField.find(Queable.class, BatchState.class);

  public static TransactionFraming transactionFraming(@Nullable JedisRequest request) {
    return new TransactionFraming(request);
  }

  public static void captureTransactionFramingPeerAddress(@Nullable Object transaction) {
    TransactionFraming framing = currentTransactionFraming().get();
    if (framing != null && framing.framingRequest != null && transaction instanceof Queable) {
      batchState((Queable) transaction).transactionFramingPeerAddress =
          framing.framingRequest.getPeerAddress();
    }
  }

  public static boolean inTransactionFraming() {
    return currentTransactionFraming().get() != null;
  }

  public static void captureTransactionFramingPeer(JedisRequest request) {
    TransactionFraming framing = currentTransactionFraming().get();
    if (framing == null) {
      return;
    }
    if (framing.transactionRequest != null) {
      framing.transactionRequest.useLaterPeerAddress(request);
    } else {
      framing.framingRequest = request;
    }
  }

  public static boolean capture(JedisRequest request) {
    Queable batch = currentBatch().get();
    if (batch == null) {
      return false;
    }
    batchState(batch).requests.add(request);
    return true;
  }

  @Nullable
  public static BatchState takeBatchState(Object batch) {
    if (!(batch instanceof Queable)) {
      return null;
    }
    Queable queable = (Queable) batch;
    BatchState state = BATCH_STATE.get(queable);
    BATCH_STATE.set(queable, null);
    return state;
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

  private JedisPipelineContext() {}

  public static final class TransactionFraming {
    @Nullable private final JedisRequest transactionRequest;
    @Nullable private JedisRequest framingRequest;

    private TransactionFraming(@Nullable JedisRequest transactionRequest) {
      this.transactionRequest = transactionRequest;
    }
  }

  public static final class BatchState {
    private final List<JedisRequest> requests = new ArrayList<>();
    @Nullable private InetSocketAddress transactionFramingPeerAddress;

    private BatchState() {}

    public List<JedisRequest> getRequests() {
      return requests;
    }

    @Nullable
    public InetSocketAddress getTransactionFramingPeerAddress() {
      return transactionFramingPeerAddress;
    }
  }
}
