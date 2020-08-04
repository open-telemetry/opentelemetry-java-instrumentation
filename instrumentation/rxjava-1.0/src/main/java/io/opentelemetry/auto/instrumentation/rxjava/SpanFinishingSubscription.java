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

package io.opentelemetry.auto.instrumentation.rxjava;

import io.opentelemetry.instrumentation.library.api.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscription;

public class SpanFinishingSubscription implements Subscription {
  private final BaseDecorator decorator;
  private final AtomicReference<Span> spanRef;

  public SpanFinishingSubscription(
      final BaseDecorator decorator, final AtomicReference<Span> spanRef) {
    this.decorator = decorator;
    this.spanRef = spanRef;
  }

  @Override
  public void unsubscribe() {
    Span span = spanRef.getAndSet(null);
    if (span != null) {
      decorator.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public boolean isUnsubscribed() {
    return spanRef.get() == null;
  }
}
