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
import com.googlecode.jsonrpc4j.ReflectionUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_3.SimpleJsonRpcRequest;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_3.SimpleJsonRpcResponse;
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

  @SuppressWarnings({"unused", "unchecked"})
  public static class CreateClientProxyAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> void onExit(
        @Advice.Argument(0) ClassLoader classLoader,
        @Advice.Argument(1) Class<T> proxyInterface,
        @Advice.Argument(2) IJsonRpcClient client,
        @Advice.Argument(3) Map<String, String> extraHeaders,
        @Advice.Return(readOnly = false) Object proxy) {

      proxy =
          (T)
              Proxy.newProxyInstance(
                  classLoader,
                  new Class<?>[] {proxyInterface},
                  new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args)
                        throws Throwable {

                      Object arguments = ReflectionUtil.parseArguments(method, args);
                      String methodName = method.getName(); // todo

                      // before invoke
                      Context parentContext = Context.current();
                      SimpleJsonRpcRequest request = new SimpleJsonRpcRequest(method, args);
                      if (!CLIENT_INSTRUMENTER.shouldStart(parentContext, request)) {
                        return client.invoke(
                            methodName, arguments, method.getGenericReturnType(), extraHeaders);
                      }

                      Context context = CLIENT_INSTRUMENTER.start(parentContext, request);
                      Scope scope = context.makeCurrent();
                      try {
                        Object result =
                            client.invoke(
                                methodName, arguments, method.getGenericReturnType(), extraHeaders);
                        scope.close();
                        CLIENT_INSTRUMENTER.end(
                            context,
                            new SimpleJsonRpcRequest(method, args),
                            new SimpleJsonRpcResponse(result),
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
