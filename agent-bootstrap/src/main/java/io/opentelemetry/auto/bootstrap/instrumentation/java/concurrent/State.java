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

package io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent;

import io.grpc.Context;
import io.opentelemetry.auto.bootstrap.ContextStore;
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

  private final AtomicReference<Context> parentContextRef = new AtomicReference<>(null);

  private State() {}

  public void setParentSpan(final Context parentContext) {
    final boolean result = parentContextRef.compareAndSet(null, parentContext);
    if (!result && parentContextRef.get() != parentContext) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Failed to set parent context because another parent context is already set {}: new: {}, old: {}",
            this,
            parentContext,
            parentContextRef.get());
      }
    }
  }

  public void clearParentContext() {
    parentContextRef.set(null);
  }

  public Context getAndResetParentContext() {
    return parentContextRef.getAndSet(null);
  }
}
