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
package io.opentelemetry.auto.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.Bridging.toShaded;
import static io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.Bridging.toUnshaded;

import java.util.List;
import unshaded.io.opentelemetry.context.propagation.HttpTextFormat;
import unshaded.io.opentelemetry.trace.SpanContext;

class UnshadedHttpTextFormat implements HttpTextFormat<SpanContext> {

  private final io.opentelemetry.context.propagation.HttpTextFormat<
          io.opentelemetry.trace.SpanContext>
      shadedHttpTextFormat;

  UnshadedHttpTextFormat(
      final io.opentelemetry.context.propagation.HttpTextFormat<io.opentelemetry.trace.SpanContext>
          shadedHttpTextFormat) {
    this.shadedHttpTextFormat = shadedHttpTextFormat;
  }

  @Override
  public List<String> fields() {
    return shadedHttpTextFormat.fields();
  }

  @Override
  public <C> SpanContext extract(final C carrier, final HttpTextFormat.Getter<C> getter) {
    return toUnshaded(shadedHttpTextFormat.extract(carrier, new UnshadedGetter<>(getter)));
  }

  @Override
  public <C> void inject(
      final SpanContext value, final C carrier, final HttpTextFormat.Setter<C> setter) {
    shadedHttpTextFormat.inject(toShaded(value), carrier, new UnshadedSetter<>(setter));
  }

  private static class UnshadedGetter<C>
      implements io.opentelemetry.context.propagation.HttpTextFormat.Getter<C> {

    private final HttpTextFormat.Getter<C> shadedGetter;

    UnshadedGetter(final HttpTextFormat.Getter<C> shadedGetter) {
      this.shadedGetter = shadedGetter;
    }

    @Override
    public String get(final C carrier, final String key) {
      return shadedGetter.get(carrier, key);
    }
  }

  private static class UnshadedSetter<C>
      implements io.opentelemetry.context.propagation.HttpTextFormat.Setter<C> {

    private final HttpTextFormat.Setter<C> shadedSetter;

    UnshadedSetter(final Setter<C> shadedSetter) {
      this.shadedSetter = shadedSetter;
    }

    @Override
    public void set(final C carrier, final String key, final String value) {
      shadedSetter.set(carrier, key, value);
    }
  }
}
