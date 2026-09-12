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
    private static final ClassValue<Method> remoteAddressMethod =
        new ClassValue<Method>() {
          @Nullable
          @Override
          protected Method computeValue(Class<?> type) {
            try {
              return type.getMethod("remoteAddress");
            } catch (NoSuchMethodException ignored) {
              return null;
            }
          }
        };

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.FieldValue("channel") Object channel)
        throws ReflectiveOperationException {
      if (channel == null) {
        return;
      }
      Method method = remoteAddressMethod.get(channel.getClass());
      if (method != null) {
        SocketAddress remoteAddress = (SocketAddress) method.invoke(channel);
        MongoConnectionPeer.capture(remoteAddress);
      }
    }
  }
}
