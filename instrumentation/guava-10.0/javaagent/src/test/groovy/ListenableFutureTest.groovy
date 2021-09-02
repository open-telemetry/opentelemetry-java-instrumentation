/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.opentelemetry.instrumentation.test.base.AbstractPromiseTest
import spock.lang.Shared

import java.util.concurrent.Executors

class ListenableFutureTest extends AbstractPromiseTest<SettableFuture<Boolean>, ListenableFuture<String>> {
  @Shared
  def executor = Executors.newFixedThreadPool(1)

  @Override
  SettableFuture<Boolean> newPromise() {
    return SettableFuture.create()
  }

  @Override
  ListenableFuture<String> map(SettableFuture<Boolean> promise, Closure<String> callback) {
    return Futures.transform(promise, callback, executor)
  }

  @Override
  void onComplete(ListenableFuture<String> promise, Closure callback) {
    promise.addListener({ -> callback(promise.get()) }, executor)
  }


  @Override
  void complete(SettableFuture<Boolean> promise, boolean value) {
    promise.set(value)
  }

  @Override
  Boolean get(SettableFuture<Boolean> promise) {
    return promise.get()
  }
}
