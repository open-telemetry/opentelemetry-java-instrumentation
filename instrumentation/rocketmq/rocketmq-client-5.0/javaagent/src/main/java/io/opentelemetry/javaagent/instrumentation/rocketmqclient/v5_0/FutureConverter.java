/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.Futures;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.MoreExecutors;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.SettableFuture;

/** Future converter, which covert future of list into list of future. */
public class FutureConverter {
  private FutureConverter() {}

  public static <T> List<SettableFuture<T>> convert(SettableFuture<List<T>> future, int num) {
    List<SettableFuture<T>> futures = new ArrayList<>(num);
    for (int i = 0; i < num; i++) {
      SettableFuture<T> f = SettableFuture.create();
      futures.add(f);
    }
    ListFutureCallback<T> futureCallback = new ListFutureCallback<>(futures);
    Futures.addCallback(future, futureCallback, MoreExecutors.directExecutor());
    return futures;
  }

  public static class ListFutureCallback<T> implements FutureCallback<List<T>> {
    private final List<SettableFuture<T>> futures;

    public ListFutureCallback(List<SettableFuture<T>> futures) {
      this.futures = futures;
    }

    @Override
    public void onSuccess(List<T> result) {
      for (int i = 0; i < result.size(); i++) {
        futures.get(i).set(result.get(i));
      }
    }

    @Override
    public void onFailure(Throwable t) {
      for (SettableFuture<T> future : futures) {
        future.setException(t);
      }
    }
  }
}
