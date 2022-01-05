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
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.internal.disposables.DisposableHelper;

public class TracingCompletableObserver implements CompletableObserver, Disposable {

  private final CompletableObserver actual;
  private final Context context;
  private Disposable disposable;

  public TracingCompletableObserver(CompletableObserver actual, Context context) {
    this.actual = actual;
    this.context = context;
  }

  @Override
  public void onSubscribe(Disposable d) {
    if (!DisposableHelper.validate(disposable, d)) {
      return;
    }
    disposable = d;
    actual.onSubscribe(this);
  }

  @Override
  public void onComplete() {
    try (Scope ignored = context.makeCurrent()) {
      actual.onComplete();
    }
  }

  @Override
  public void onError(Throwable e) {
    try (Scope ignored = context.makeCurrent()) {
      actual.onError(e);
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
