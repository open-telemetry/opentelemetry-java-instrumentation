/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.http.scaladsl.HttpExt;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;

public class AkkaHttpClientUtil {
  @Nullable private static final MethodHandle ACTOR_SYSTEM_ACCESSOR = findActorSystemAccessor();

  @Nullable
  private static MethodHandle findActorSystemAccessor() {
    MethodHandle methodHandle = findActorSystemAccessor(ExtendedActorSystem.class);
    if (methodHandle != null) {
      return methodHandle;
    }
    return findActorSystemAccessor(ActorSystem.class);
  }

  @Nullable
  private static MethodHandle findActorSystemAccessor(Class<?> type) {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(HttpExt.class, "system", MethodType.methodType(type));
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Nullable
  public static ActorSystem getActorSystem(HttpExt httpExt) {
    if (ACTOR_SYSTEM_ACCESSOR == null) {
      return null;
    }

    try {
      return (ActorSystem) ACTOR_SYSTEM_ACCESSOR.invoke(httpExt);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private AkkaHttpClientUtil() {}
}
