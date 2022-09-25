/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.http.protocol.ExecutionContext.HTTP_REQUEST;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class ApacheCloseableHttpClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.http.client.HttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.http.impl.client.CloseableHttpClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("doExecute"))
            .and(isProtected())
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        this.getClass().getName() + "$RequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) HttpRequest request,
        @Advice.Argument(value = 2, readOnly = false) HttpContext httpContext,
        @Advice.Local("otelRequest") ApacheHttpClientRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();

      if (httpContext == null) {
        httpContext = new BasicHttpContext();
      }

      otelRequest = new ApacheHttpClientRequest(host, request);

      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, otelRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(2) HttpContext httpContext,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") ApacheHttpClientRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      HttpRequest httpRequest = (HttpRequest) httpContext.getAttribute(HTTP_REQUEST);
      otelRequest = otelRequest.withRequest(httpRequest);
      ApacheHttpClientHelper.doMethodExit(context, otelRequest, result, throwable);
    }
  }
}
