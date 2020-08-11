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

package io.opentelemetry.instrumentation.auto.api.rmi;

import io.opentelemetry.trace.SpanContext;

public class ThreadLocalContext {
  public static final ThreadLocalContext THREAD_LOCAL_CONTEXT = new ThreadLocalContext();
  private final ThreadLocal<SpanContext> local;

  public ThreadLocalContext() {
    local = new ThreadLocal<>();
  }

  public void set(final SpanContext context) {
    local.set(context);
  }

  public SpanContext getAndResetContext() {
    SpanContext context = local.get();
    if (context != null) {
      local.remove();
    }
    return context;
  }
}
