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

package io.opentelemetry.instrumentation.rxjava.v3.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.internal.disposables.DisposableHelper;

public class TracingSingleObserver<T> implements SingleObserver<T>, Disposable {

  private final SingleObserver<T> actual;
  private final Context context;
  private Disposable disposable;

  public TracingSingleObserver(SingleObserver<T> actual, Context context) {
    this.actual = actual;
    this.context = context;
  }

  @Override
  public void onSubscribe(Disposable d) {
    if (!DisposableHelper.validate(disposable, d)) {
      return;
    }
    this.disposable = d;
    actual.onSubscribe(this);
  }

  @Override
  public void onSuccess(T t) {
    try (Scope ignored = context.makeCurrent()) {
      actual.onSuccess(t);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    try (Scope ignored = context.makeCurrent()) {
      actual.onError(throwable);
    }
  }

  @Override
  public void dispose() {
    disposable.dispose();
  }

  @Override
  public boolean isDisposed() {
    return disposable.isDisposed();
  }
}
