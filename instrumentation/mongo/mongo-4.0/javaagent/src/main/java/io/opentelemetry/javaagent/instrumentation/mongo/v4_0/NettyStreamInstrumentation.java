/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class NettyStreamInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.internal.connection.netty.NettyStream");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("open"), getClass().getName() + "$OpenAdvice");
  }

  @SuppressWarnings("unused")
  public static class OpenAdvice {
    @Nullable private static final Method remoteAddressMethod = findRemoteAddressMethod();

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.FieldValue("channel") Object channel)
        throws ReflectiveOperationException {
      if (channel == null) {
        return;
      }
      if (remoteAddressMethod != null) {
        SocketAddress remoteAddress = (SocketAddress) remoteAddressMethod.invoke(channel);
        MongoConnectionPeer.capture(remoteAddress);
      }
    }

    @Nullable
    private static Method findRemoteAddressMethod() {
      try {
        Class<?> channelClass =
            Class.forName("io.netty.channel.Channel", false, OpenAdvice.class.getClassLoader());
        return channelClass.getMethod("remoteAddress");
      } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
        return null;
      }
    }
  }
}
