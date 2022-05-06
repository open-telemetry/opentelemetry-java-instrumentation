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

package io.opentelemetry.instrumentation.rxjava.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.internal.subscribers.BasicFuseableConditionalSubscriber;

class TracingConditionalSubscriber<T> extends BasicFuseableConditionalSubscriber<T, T> {

  // BasicFuseableConditionalSubscriber#actual has been renamed to downstream in newer versions, we
  // can't use it in this class
  private final ConditionalSubscriber<? super T> wrappedSubscriber;
  private final Context context;

  TracingConditionalSubscriber(ConditionalSubscriber<? super T> actual, Context context) {
    super(actual);
    this.wrappedSubscriber = actual;
    this.context = context;
  }

  @Override
  public boolean tryOnNext(T t) {
    try (Scope ignored = context.makeCurrent()) {
      return wrappedSubscriber.tryOnNext(t);
    }
  }

  @Override
  public void onNext(T t) {
    try (Scope ignored = context.makeCurrent()) {
      wrappedSubscriber.onNext(t);
    }
  }

  @Override
  public void onError(Throwable t) {
    try (Scope ignored = context.makeCurrent()) {
      wrappedSubscriber.onError(t);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = context.makeCurrent()) {
      wrappedSubscriber.onComplete();
    }
  }

  @Override
  public int requestFusion(int mode) {
    QueueSubscription<T> qs = this.qs;
    if (qs != null) {
      int m = qs.requestFusion(mode);
      sourceMode = m;
      return m;
    }
    return NONE;
  }

  @Override
  public T poll() throws Exception {
    return qs.poll();
  }
}
