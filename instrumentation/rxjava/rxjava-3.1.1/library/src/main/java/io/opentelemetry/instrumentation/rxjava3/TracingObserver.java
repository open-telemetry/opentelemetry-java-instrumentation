/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.opentelemetry.instrumentation.rxjava3;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.internal.observers.BasicFuseableObserver;
import io.reactivex.rxjava3.operators.QueueDisposable;

class TracingObserver<T> extends BasicFuseableObserver<T, T> {

  private final Context context;

  TracingObserver(Observer<? super T> downstream, Context context) {
    super(downstream);
    this.context = context;
  }

  @Override
  public void onNext(T t) {
    try (Scope ignored = context.makeCurrent()) {
      downstream.onNext(t);
    }
  }

  @Override
  public void onError(Throwable t) {
    try (Scope ignored = context.makeCurrent()) {
      downstream.onError(t);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = context.makeCurrent()) {
      downstream.onComplete();
    }
  }

  @Override
  public int requestFusion(int mode) {
    QueueDisposable<T> qd = this.qd;
    if (qd != null) {
      int m = qd.requestFusion(mode);
      sourceMode = m;
      return m;
    }
    return NONE;
  }

  @Override
  public T poll() throws Throwable {
    return qd.poll();
  }
}
