/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinSingletons.rpcInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.vaadin.flow.server.communication.rpc.RpcInvocationHandler;
import elemental.json.JsonObject;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// add span around rpc calls from javascript
public class RpcInvocationHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.vaadin.flow.server.communication.rpc.RpcInvocationHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(
        named("com.vaadin.flow.server.communication.rpc.RpcInvocationHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handle")
            .and(takesArgument(0, named("com.vaadin.flow.component.UI")))
            .and(takesArgument(1, named("elemental.json.JsonObject"))),
        this.getClass().getName() + "$HandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleAdvice {

    public static class AdviceScope {
      private final VaadinRpcRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(VaadinRpcRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(
          RpcInvocationHandler handler, String methodName, JsonObject jsonObject) {
        Context parentContext = Context.current();
        VaadinRpcRequest request = VaadinRpcRequest.create(handler, methodName, jsonObject);
        if (!rpcInstrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = rpcInstrumenter().start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();

        rpcInstrumenter().end(context, request, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This RpcInvocationHandler rpcInvocationHandler,
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(1) JsonObject jsonObject) {
      return AdviceScope.start(rpcInvocationHandler, methodName, jsonObject);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
