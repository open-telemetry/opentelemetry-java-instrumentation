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

package io.opentelemetry.auto.instrumentation.opentelemetryapi.context.propagation;

import io.opentelemetry.auto.bootstrap.ContextStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unshaded.io.grpc.Context;
import unshaded.io.opentelemetry.context.propagation.HttpTextFormat;

class UnshadedHttpTextFormat implements HttpTextFormat {

  private static final Logger log = LoggerFactory.getLogger(UnshadedHttpTextFormat.class);

  private final io.opentelemetry.context.propagation.HttpTextFormat shadedHttpTextFormat;
  private final ContextStore<Context, io.grpc.Context> contextStore;

  UnshadedHttpTextFormat(
      final io.opentelemetry.context.propagation.HttpTextFormat shadedHttpTextFormat,
      final ContextStore<Context, io.grpc.Context> contextStore) {
    this.shadedHttpTextFormat = shadedHttpTextFormat;
    this.contextStore = contextStore;
  }

  @Override
  public List<String> fields() {
    return shadedHttpTextFormat.fields();
  }

  @Override
  public <C> Context extract(
      final Context context, final C carrier, final HttpTextFormat.Getter<C> getter) {
    final io.grpc.Context shadedContext = contextStore.get(context);
    if (shadedContext == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected context: {}", context, new Exception("unexpected context"));
      }
      return context;
    }
    final io.grpc.Context updatedShadedContext =
        shadedHttpTextFormat.extract(shadedContext, carrier, new UnshadedGetter<>(getter));
    if (updatedShadedContext == shadedContext) {
      return context;
    }
    contextStore.put(context, updatedShadedContext);
    return context;
  }

  @Override
  public <C> void inject(
      final Context context, final C carrier, final HttpTextFormat.Setter<C> setter) {
    final io.grpc.Context shadedContext = contextStore.get(context);
    if (shadedContext == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected context: {}", context, new Exception("unexpected context"));
      }
      return;
    }
    shadedHttpTextFormat.inject(shadedContext, carrier, new UnshadedSetter<>(setter));
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
