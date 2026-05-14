/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConnection;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class GrpcConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.alibaba.nacos.common.remote.client.grpc.GrpcConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("request"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.alibaba.nacos.api.remote.request.Request")))
            .and(takesArgument(1, long.class)),
        getClass().getName() + "$RequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static State onEnter(
        @Advice.This GrpcConnection connection, @Advice.Argument(0) Request request) {
      NacosClientRequest nacosRequest =
          NacosRequestMapper.mapClientRequest(request, connection.getChannel().authority());
      if (nacosRequest == null) {
        return null;
      }
      ContextAndScope contextAndScope = NacosClientSingletons.startClientSpan(nacosRequest);
      return contextAndScope == null ? null : new State(nacosRequest, contextAndScope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable State state,
        @Advice.Return @Nullable Response response,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (state == null) {
        return;
      }
      NacosClientSingletons.endClientSpan(
          state.contextAndScope(), state.request(), response, throwable);
    }
  }

  public static final class State {
    private final NacosClientRequest request;
    private final ContextAndScope contextAndScope;

    public State(NacosClientRequest request, ContextAndScope contextAndScope) {
      this.request = request;
      this.contextAndScope = contextAndScope;
    }

    public NacosClientRequest request() {
      return request;
    }

    public ContextAndScope contextAndScope() {
      return contextAndScope;
    }
  }
}
