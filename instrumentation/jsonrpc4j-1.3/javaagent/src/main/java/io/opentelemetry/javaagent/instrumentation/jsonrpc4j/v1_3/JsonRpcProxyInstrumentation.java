/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_3.JsonRpcSingletons.CLIENT_INSTRUMENTER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.googlecode.jsonrpc4j.IJsonRpcClient;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_3.JsonRpcClientRequest;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_3.JsonRpcClientResponse;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JsonRpcProxyInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.googlecode.jsonrpc4j.ProxyUtil");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.googlecode.jsonrpc4j.ProxyUtil");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(isPrivate()).and(named("createClientProxy")),
        this.getClass().getName() + "$CreateClientProxyAdvice");
  }

  @SuppressWarnings({"unused"})
  public static class CreateClientProxyAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> void onExit(
        @Advice.Argument(0) ClassLoader classLoader,
        @Advice.Argument(1) Class<T> proxyInterface,
        @Advice.Argument(2) IJsonRpcClient client,
        @Advice.Argument(3) Map<String, String> extraHeaders,
        @Advice.Return(readOnly = false) Object proxy) {

      proxy = instrumentCreateClientProxy(classLoader, proxyInterface, client, extraHeaders, proxy);
    }

    private static Object proxyObjectMethods(Method method, Object proxyObject, Object[] args) {
      String name = method.getName();
      switch (name) {
        case "toString":
          return proxyObject.getClass().getName() + "@" + System.identityHashCode(proxyObject);
        case "hashCode":
          return System.identityHashCode(proxyObject);
        case "equals":
          return proxyObject == args[0];
        default:
          throw new IllegalArgumentException(
              method.getName() + " is not a member of java.lang.Object");
      }
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T instrumentCreateClientProxy(
        ClassLoader classLoader,
        Class<T> proxyInterface,
        IJsonRpcClient client,
        Map<String, String> extraHeaders,
        Object proxy) {

      return (T)
          Proxy.newProxyInstance(
              classLoader,
              new Class<?>[] {proxyInterface},
              new InvocationHandler() {
                @Override
                public Object invoke(Object proxy1, Method method, Object[] args) throws Throwable {
                  // before invoke
                  Context parentContext = Context.current();
                  JsonRpcClientRequest request = new JsonRpcClientRequest(method, args);
                  if (!CLIENT_INSTRUMENTER.shouldStart(parentContext, request)) {
                    return method.invoke(proxy, args);
                  }

                  Context context = CLIENT_INSTRUMENTER.start(parentContext, request);
                  Scope scope = context.makeCurrent();
                  try {
                    Object result = method.invoke(proxy, args);
                    // after invoke
                    scope.close();
                    CLIENT_INSTRUMENTER.end(
                        context,
                        new JsonRpcClientRequest(method, args),
                        new JsonRpcClientResponse(result),
                        null);
                    return result;

                  } catch (Throwable t) {
                    // after invoke
                    scope.close();
                    CLIENT_INSTRUMENTER.end(context, request, null, t);
                    throw t;
                  }
                }
              });
    }
  }
}
