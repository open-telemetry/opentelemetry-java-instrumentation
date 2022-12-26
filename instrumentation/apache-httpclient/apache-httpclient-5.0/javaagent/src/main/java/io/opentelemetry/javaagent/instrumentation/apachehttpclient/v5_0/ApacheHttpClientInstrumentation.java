/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.createOrGetBytesTransferMetrics;
import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

class ApacheHttpClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.hc.client5.http.impl.classic.CloseableHttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.hc.client5.http.impl.classic.CloseableHttpClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("doExecute"))
            .and(isProtected())
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.protocol.HttpContext"))),
        this.getClass().getName() + "$RequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class RequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) ClassicHttpRequest request,
        @Advice.Argument(value = 2, readOnly = false) HttpContext httpContext,
        @Advice.Local("otelRequest") ApacheHttpClientRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (httpContext == null) {
        httpContext = new BasicHttpContext();
      }
      RequestWithHost requestWithHost = new RequestWithHost(host, request);
      otelRequest = ApacheHttpClientHelper.createRequest(parentContext, requestWithHost);
      if (instrumenter().shouldStart(parentContext, otelRequest)) {
        HttpEntity entity = request.getEntity();
        if (entity != null) {
          long contentLength = entity.getContentLength();
          BytesTransferMetrics metrics = createOrGetBytesTransferMetrics(parentContext);
          metrics.setRequestContentLength(contentLength);
        }
        context = instrumenter().start(parentContext, otelRequest);
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return CloseableHttpResponse response,
        @Advice.Argument(2) HttpContext httpContext,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") ApacheHttpClientRequest otelRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
        // Replacing with actual request:
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/6747
        HttpCoreContext coreContext = HttpCoreContext.adapt(httpContext);
        HttpRequest request = coreContext.getRequest();
        if (request != null) {
          otelRequest = new ApacheHttpClientRequest(otelRequest.getParentContext(), request);
        }
        if (response != null) {
          HttpEntity entity = response.getEntity();
          if (entity != null) {
            long contentLength = entity.getContentLength();
            Context parentContext = otelRequest.getParentContext();
            BytesTransferMetrics metrics = createOrGetBytesTransferMetrics(parentContext);
            metrics.setResponseContentLength(contentLength);
          }
        }
        if (throwable != null) {
          instrumenter().end(context, otelRequest, null, throwable);
        } else {
          instrumenter().end(context, otelRequest, response, null);
        }
      }
    }
  }
}
