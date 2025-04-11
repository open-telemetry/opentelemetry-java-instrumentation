/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.catseffect.common.v3_6;

import application.io.opentelemetry.context.Context;
import cats.effect.IOLocal;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;

public class IoLocalContextSingleton {

  private IoLocalContextSingleton() {}

  public static final IOLocal<Context> ioLocal =
      IOLocal.apply(Context.root()).syncStep(100).unsafeRunSync().toOption().get();

  private static final ThreadLocal<Context> ioLocalThreadLocal = ioLocal.unsafeThreadLocal();

  public static final ThreadLocal<io.opentelemetry.context.Context> contextThreadLocal =
      new ThreadLocal<io.opentelemetry.context.Context>() {
        @Override
        public io.opentelemetry.context.Context get() {
          Context current = ioLocalThreadLocal.get();
          return current != null ? AgentContextStorage.getAgentContext(current) : null;
        }

        @Override
        public void set(io.opentelemetry.context.Context value) {
          if (value != null) {
            ioLocalThreadLocal.set(AgentContextStorage.toApplicationContext(value));
          } else {
            ioLocalThreadLocal.remove();
          }
        }
      };
}
