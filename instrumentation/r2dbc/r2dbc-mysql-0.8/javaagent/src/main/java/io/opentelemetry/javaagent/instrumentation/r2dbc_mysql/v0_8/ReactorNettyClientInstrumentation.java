/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc_mysql.v0_8;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import dev.miku.r2dbc.mysql.client.Client;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.jdbc.internal.DbInfo;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.netty.Connection;

public class ReactorNettyClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("dev.miku.r2dbc.mysql.client.ReactorNettyClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), ReactorNettyClientInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onConstruct(
        @Advice.This Client instance, @Advice.Argument(0) Connection connection) {
      InetSocketAddress address = (InetSocketAddress) connection.channel().remoteAddress();
      DbInfo dbInfo =
          DbInfo.builder()
              .host(address.getHostName())
              .port(address.getPort())
              .system(SemanticAttributes.DbSystemValues.MYSQL)
              .subtype("network")
              .build();
      VirtualField.find(Client.class, DbInfo.class).set(instance, dbInfo);
    }
  }
}
