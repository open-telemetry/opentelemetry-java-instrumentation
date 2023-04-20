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

@SuppressWarnings("unchecked")
public final class TracingSupervisor extends Supervisor<Object> {

  @SuppressWarnings("rawtypes")
  private final VirtualField<Fiber.Runtime, FiberContext> virtualField;

  @SuppressWarnings("rawtypes")
  public TracingSupervisor(VirtualField<Fiber.Runtime, FiberContext> virtualField) {
    this.virtualField = virtualField;
  }

  @Override
  @SuppressWarnings("rawtypes")
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
    virtualField.set(fiber, context);
  }

  @Override
  public <R, E, A1> void onEnd(Exit<E, A1> value, Fiber.Runtime<E, A1> fiber, Unsafe unsafe) {}

  @Override
  public <E, A1> void onSuspend(Fiber.Runtime<E, A1> fiber, Unsafe unsafe) {
    FiberContext context = virtualField.get(fiber);
    if (context != null) {
      context.onSuspend();
    }
  }

  @Override
  public <E, A1> void onResume(Fiber.Runtime<E, A1> fiber, Unsafe unsafe) {
    FiberContext context = virtualField.get(fiber);
    if (context != null) {
      context.onResume();
    }
  }
}
