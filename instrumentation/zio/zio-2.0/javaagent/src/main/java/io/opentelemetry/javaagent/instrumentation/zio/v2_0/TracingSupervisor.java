/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import scala.Option;
import zio.Exit;
import zio.Fiber;
import zio.Supervisor;
import zio.Unsafe;
import zio.ZEnvironment;
import zio.ZIO;
import zio.ZIO$;

@SuppressWarnings("unchecked") // fine
public final class TracingSupervisor extends Supervisor<Object> {

  public static final TracingSupervisor INSTANCE = new TracingSupervisor();
  private static final VirtualField<Fiber.Runtime<?, ?>, FiberContext> RUNTIME_FIBER_CONTEXT =
      VirtualField.find(Fiber.Runtime.class, FiberContext.class);

  private TracingSupervisor() {}

  @Override
  @SuppressWarnings("rawtypes") // fine
  public ZIO value(Object trace) {
    return ZIO$.MODULE$.unit();
  }

  @Override
  public <R, E, A1> void onStart(
      ZEnvironment<R> environment,
      ZIO<R, E, A1> effect,
      Option<Fiber.Runtime<Object, Object>> parent,
      Fiber.Runtime<E, A1> fiber,
      Unsafe unsafe) {
    FiberContext context = FiberContext.create();
    RUNTIME_FIBER_CONTEXT.set(fiber, context);
  }

  @Override
  public <R, E, A1> void onEnd(Exit<E, A1> value, Fiber.Runtime<E, A1> fiber, Unsafe unsafe) {}

  @Override
  public <E, A1> void onSuspend(Fiber.Runtime<E, A1> fiber, Unsafe unsafe) {
    FiberContext context = RUNTIME_FIBER_CONTEXT.get(fiber);
    if (context != null) {
      context.onSuspend();
    }
  }

  @Override
  public <E, A1> void onResume(Fiber.Runtime<E, A1> fiber, Unsafe unsafe) {
    FiberContext context = RUNTIME_FIBER_CONTEXT.get(fiber);
    if (context != null) {
      context.onResume();
    }
  }
}
