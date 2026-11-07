/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import java.lang.reflect.Field;
import javax.annotation.Nullable;

/**
 * Detects Vertx instances that have a {@code VertxTracer} registered. Such an application has opted
 * into Vert.x's own event bus tracing (vertx-opentelemetry); tracing the same hops again produces
 * extra spans and re-parents the consumer away from its producer, so the agent stands down.
 *
 * <p>{@code VertxTracer} doesn't exist before Vert.x 4 and can't be referenced directly without
 * failing muzzle on Vert.x 3, hence the reflection. Results are stored on the instance.
 */
final class NativeTracing {

  private static final VirtualField<EventBus, NativeTracingState> EVENT_BUS_STATE =
      VirtualField.find(EventBus.class, NativeTracingState.class);
  private static final VirtualField<Vertx, NativeTracingState> VERTX_STATE =
      VirtualField.find(Vertx.class, NativeTracingState.class);

  static boolean isEnabled(EventBus eventBus) {
    NativeTracingState state = EVENT_BUS_STATE.get(eventBus);
    if (state == null) {
      Object vertx = readField(eventBus, "vertx");
      state = NativeTracingState.of(vertx != null && hasTracer(vertx));
      EVENT_BUS_STATE.set(eventBus, state);
    }
    return state == NativeTracingState.ENABLED;
  }

  /** For consumers, which are invoked on a context of the Vertx instance they belong to. */
  static boolean isEnabledForCurrentContext() {
    Context context = Vertx.currentContext();
    if (context == null) {
      return false;
    }
    Vertx vertx = context.owner();
    NativeTracingState state = VERTX_STATE.get(vertx);
    if (state == null) {
      state = NativeTracingState.of(hasTracer(vertx));
      VERTX_STATE.set(vertx, state);
    }
    return state == NativeTracingState.ENABLED;
  }

  private static boolean hasTracer(Object vertx) {
    Object tracer = readField(vertx, "tracer");
    return tracer != null && tracer != noopTracer(vertx.getClass().getClassLoader());
  }

  // VertxTracer.NOOP was added after Vert.x 4.0, which uses null instead
  @Nullable
  private static Object noopTracer(ClassLoader classLoader) {
    try {
      return Class.forName("io.vertx.core.spi.tracing.VertxTracer", false, classLoader)
          .getField("NOOP")
          .get(null);
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private static Object readField(Object target, String name) {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
      } catch (NoSuchFieldException e) {
        // keep looking in the superclass
      } catch (Exception e) {
        return null;
      }
    }
    return null;
  }

  private NativeTracing() {}
}
