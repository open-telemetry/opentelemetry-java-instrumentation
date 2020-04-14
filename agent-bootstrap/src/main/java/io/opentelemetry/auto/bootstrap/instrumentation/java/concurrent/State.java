/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent;

import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class State {

  public static ContextStore.Factory<State> FACTORY =
      new ContextStore.Factory<State>() {
        @Override
        public State create() {
          return new State();
        }
      };

  private final AtomicReference<Span> parentSpanRef = new AtomicReference<>(null);

  private State() {}

  public void setParentSpan(final Span parentSpan) {
    final boolean result = parentSpanRef.compareAndSet(null, parentSpan);
    if (!result && parentSpanRef.get() != parentSpan) {
      log.debug(
          "Failed to set parent span because another parent span is already set {}: new: {}, old: {}",
          this,
          parentSpan,
          parentSpanRef.get());
    }
  }

  public void clearParentSpan() {
    parentSpanRef.set(null);
  }

  public Span getAndResetParentSpan() {
    return parentSpanRef.getAndSet(null);
  }
}
