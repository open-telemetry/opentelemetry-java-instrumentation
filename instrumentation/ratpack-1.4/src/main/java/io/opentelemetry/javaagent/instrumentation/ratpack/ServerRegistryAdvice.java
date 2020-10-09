/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.ratpack;

import net.bytebuddy.asm.Advice;
import ratpack.handling.HandlerDecorator;
import ratpack.registry.Registry;

public class ServerRegistryAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void injectTracing(@Advice.Return(readOnly = false) Registry registry) {
    registry =
        registry.join(
            Registry.builder().add(HandlerDecorator.prepend(TracingHandler.INSTANCE)).build());
  }
}
