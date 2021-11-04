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
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.reflect.Method;
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This RpcInvocationHandler rpcInvocationHandler,
        @Advice.Origin Method method,
        @Advice.Argument(1) JsonObject jsonObject,
        @Advice.Local("otelRequest") VaadinRpcRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = VaadinRpcRequest.create(rpcInvocationHandler, method, jsonObject);
      if (!rpcInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = rpcInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") VaadinRpcRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      rpcInstrumenter().end(context, request, null, throwable);
    }
  }
}
