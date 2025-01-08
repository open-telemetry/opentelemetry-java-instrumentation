/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_6.HeadersSetter;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_6.SimpleJsonRpcRequest;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_6.SimpleJsonRpcResponse;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JsonRpcClientBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.googlecode.jsonrpc4j.IJsonRpcClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // match JsonRpcHttpClient and JsonRpcRestClient
    return implementsInterface(named("com.googlecode.jsonrpc4j.IJsonRpcClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("invoke"))
            .and(takesArguments(4))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, Object.class))
            .and(takesArgument(2, named("java.lang.reflect.Type")))
            .and(takesArgument(3, named("java.util.Map")))
            .and(returns(Object.class)),
        this.getClass().getName() + "$InvokeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String methodName,
        @Advice.Argument(1) Object argument,
        @Advice.Argument(3) Map<String, String> extraHeaders,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Context.current();
      SimpleJsonRpcRequest request = new SimpleJsonRpcRequest(methodName, argument);
      if (!JsonRpcSingletons.CLIENT_INSTRUMENTER.shouldStart(parentContext, request)) {
        return;
      }

      context = JsonRpcSingletons.CLIENT_INSTRUMENTER.start(parentContext, request);
      JsonRpcSingletons.PROPAGATORS
          .getTextMapPropagator()
          .inject(context, extraHeaders, HeadersSetter.INSTANCE);

      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) String methodName,
        @Advice.Argument(1) Object argument,
        @Advice.Argument(3) Map<String, String> extraHeaders,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      JsonRpcSingletons.CLIENT_INSTRUMENTER.end(
          context,
          new SimpleJsonRpcRequest(methodName, argument),
          new SimpleJsonRpcResponse(result),
          throwable);
      System.out.println(extraHeaders);
    }
  }
}
