/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.opentelemetry.auto.test.base.AbstractPromiseTest
import java.util.concurrent.Executors
import spock.lang.Shared

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
