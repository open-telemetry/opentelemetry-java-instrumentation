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
package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import static io.opentelemetry.auto.bootstrap.WeakMap.Provider.newWeakMap;

import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;

public abstract class ClientDecorator extends BaseDecorator {

  // Work around the fact that we cannot read the kind of currentSpan by keeping track of the spans
  // we created.
  private static final WeakMap<Span, Boolean> CLIENT_SPANS = newWeakMap();

  public Span getOrCreateSpan(String name, Tracer tracer) {
    final Span current = tracer.getCurrentSpan();

    if (Boolean.TRUE.equals(CLIENT_SPANS.get(current))) {
      // We don't want to create two client spans for a given client call, suppress inner spans.
      return DefaultSpan.getInvalid();
    }

    final Span clientSpan =
        tracer.spanBuilder(name).setSpanKind(Kind.CLIENT).setParent(current).startSpan();
    CLIENT_SPANS.put(clientSpan, true);
    return clientSpan;
  }

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    return super.afterStart(span);
  }
}
