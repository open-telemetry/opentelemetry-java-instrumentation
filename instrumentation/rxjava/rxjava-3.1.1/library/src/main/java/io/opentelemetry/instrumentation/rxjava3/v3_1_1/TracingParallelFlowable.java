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

package io.opentelemetry.instrumentation.rxjava3.v3_1_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.rxjava3.operators.ConditionalSubscriber;
import io.reactivex.rxjava3.parallel.ParallelFlowable;
import org.reactivestreams.Subscriber;

class TracingParallelFlowable<T> extends ParallelFlowable<T> {

  private final ParallelFlowable<T> source;
  private final Context context;

  TracingParallelFlowable(ParallelFlowable<T> source, Context context) {
    this.source = source;
    this.context = context;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void subscribe(Subscriber<? super T>[] subscribers) {
    if (!validate(subscribers)) {
      return;
    }
    int n = subscribers.length;
    @SuppressWarnings("rawtypes")
    Subscriber<? super T>[] parents = new Subscriber[n];
    for (int i = 0; i < n; i++) {
      Subscriber<? super T> z = subscribers[i];
      if (z instanceof ConditionalSubscriber) {
        parents[i] =
            new TracingConditionalSubscriber<>((ConditionalSubscriber<? super T>) z, context);
      } else {
        parents[i] = new TracingSubscriber<>(z, context);
      }
    }
    try (Scope ignored = context.makeCurrent()) {
      source.subscribe(parents);
    }
  }

  @Override
  public int parallelism() {
    return source.parallelism();
  }
}
