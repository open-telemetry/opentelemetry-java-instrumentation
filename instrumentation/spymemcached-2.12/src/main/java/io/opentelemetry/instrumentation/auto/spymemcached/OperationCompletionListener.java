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

package io.opentelemetry.instrumentation.auto.spymemcached;

import io.opentelemetry.trace.Span;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.OperationFuture;

public class OperationCompletionListener
    extends CompletionListener<OperationFuture<? extends Object>>
    implements net.spy.memcached.internal.OperationCompletionListener {
  public OperationCompletionListener(
      MemcachedConnection connection, String methodName) {
    super(connection, methodName);
  }

  @Override
  public void onComplete(OperationFuture<? extends Object> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(Span span, OperationFuture<? extends Object> future)
      throws ExecutionException, InterruptedException {
    future.get();
  }
}
