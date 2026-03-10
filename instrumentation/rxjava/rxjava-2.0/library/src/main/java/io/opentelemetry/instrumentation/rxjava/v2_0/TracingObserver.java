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
import io.reactivex.Observer;
import io.reactivex.internal.fuseable.QueueDisposable;
import io.reactivex.internal.observers.BasicFuseableObserver;
import java.lang.reflect.Field;

class TracingObserver<T> extends BasicFuseableObserver<T, T> {
  private static final Field queueDisposableField = getQueueDisposableField();

  // BasicFuseableObserver#actual has been renamed to downstream in newer versions, we can't use it
  // in this class
  private final Observer<? super T> wrappedObserver;
  private final Context context;

  TracingObserver(Observer<? super T> actual, Context context) {
    super(actual);
    this.wrappedObserver = actual;
    this.context = context;
  }

  @Override
  public void onNext(T t) {
    try (Scope ignored = context.makeCurrent()) {
      wrappedObserver.onNext(t);
    }
  }

  @Override
  public void onError(Throwable t) {
    try (Scope ignored = context.makeCurrent()) {
      wrappedObserver.onError(t);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = context.makeCurrent()) {
      wrappedObserver.onComplete();
    }
  }

  @Override
  public int requestFusion(int mode) {
    QueueDisposable<T> qd = getQueueDisposable();
    if (qd != null) {
      int m = qd.requestFusion(mode);
      sourceMode = m;
      return m;
    }
    return NONE;
  }

  @Override
  public T poll() throws Exception {
    return getQueueDisposable().poll();
  }

  @SuppressWarnings("unchecked") // casting reflection result
  private QueueDisposable<T> getQueueDisposable() {
    try {
      return (QueueDisposable<T>) queueDisposableField.get(this);
    } catch (Throwable throwable) {
      throw new IllegalStateException(throwable);
    }
  }

  private static Field getField(String fieldName) {

    try {
      return BasicFuseableObserver.class.getDeclaredField(fieldName);
    } catch (NoSuchFieldException ignored) {
      // Ignore
    }
    return null;
  }

  private static Field getQueueDisposableField() {
    Field queueDisposableField = getField("qd");
    if (queueDisposableField == null) {
      // in versions before 2.2.1 field was named "qs"
      queueDisposableField = getField("qs");
    }
    return queueDisposableField;
  }

  public static boolean canEnable() {
    return queueDisposableField != null;
  }
}
