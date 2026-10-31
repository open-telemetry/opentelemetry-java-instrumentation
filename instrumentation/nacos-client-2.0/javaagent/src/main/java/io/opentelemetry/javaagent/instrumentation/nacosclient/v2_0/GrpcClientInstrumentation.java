/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.client.RpcClient;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class GrpcClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.alibaba.nacos.common.remote.client.grpc.GrpcClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("serverCheck"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, int.class))
            .and(
                takesArgument(
                    2, named("com.alibaba.nacos.api.grpc.auto.RequestGrpc$RequestFutureStub"))),
        getClass().getName() + "$ServerCheckAdvice");
  }

  @SuppressWarnings("unused")
  public static class ServerCheckAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ContextAndScope onEnter(
        @Advice.This RpcClient rpcClient,
        @Advice.Argument(0) String serverIp,
        @Advice.Argument(1) int serverPort) {
      RpcClientServerInfoAccessor.set(rpcClient, new RpcClient.ServerInfo(serverIp, serverPort));
      return NacosClientSingletons.startClientSpan(
          NacosRequestMapper.createServerCheckRequest(serverIp, serverPort));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) String serverIp,
        @Advice.Argument(1) int serverPort,
        @Advice.Enter @Nullable ContextAndScope contextAndScope,
        @Advice.Return @Nullable Response response,
        @Advice.Thrown @Nullable Throwable throwable) {
      NacosClientSingletons.endClientSpan(
          contextAndScope,
          NacosRequestMapper.createServerCheckRequest(serverIp, serverPort),
          response,
          throwable);
    }
  }
}
