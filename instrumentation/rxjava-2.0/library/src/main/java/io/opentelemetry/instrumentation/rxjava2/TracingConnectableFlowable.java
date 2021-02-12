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

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import org.reactivestreams.Subscriber;

class TracingConnectableFlowable<T> extends ConnectableFlowable<T> {

  private final ConnectableFlowable<T> source;
  private final Context context;

  TracingConnectableFlowable(final ConnectableFlowable<T> source, final Context context) {
    this.source = source;
    this.context = context;
  }

  @Override
  public void connect(final Consumer<? super Disposable> connection) {
    try (Scope ignored = context.makeCurrent()) {
      source.connect(connection);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void subscribeActual(final Subscriber<? super T> s) {
    if (s instanceof ConditionalSubscriber) {
      source.subscribe(
          new TracingConditionalSubscriber<>((ConditionalSubscriber<? super T>) s, context));
    } else {
      source.subscribe(new TracingSubscriber<>(s, context));
    }
  }
}
