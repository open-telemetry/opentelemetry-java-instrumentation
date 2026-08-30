/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.Socket;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class SocketStreamInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.mongodb.internal.connection.SocketStream",
        "com.mongodb.internal.connection.UnixSocketChannelStream");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Async streams use separate channel implementations and are intentionally not covered.
    transformer.applyAdviceToMethod(
        named("initializeSocket").and(isProtected()).and(returns(named("java.net.Socket"))),
        getClass().getName() + "$InitializeSocketAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitializeSocketAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Return Socket socket) {
      MongoConnectionPeer.capture(socket);
    }
  }
}
