/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.jaxrsclient.v1_1;

import static io.opentelemetry.auto.instrumentation.jaxrsclient.v1_1.InjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.jaxrsclient.v1_1.JaxRsClientV1Decorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jaxrsclient.v1_1.JaxRsClientV1Decorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.api.decorator.HttpServerTracer.CONTEXT_ATTRIBUTE;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JaxRsClientV1Instrumentation extends Instrumenter.Default {

  public JaxRsClientV1Instrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-client");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("com.sun.jersey.api.client.ClientHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.sun.jersey.api.client.ClientHandler"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JaxRsClientV1Decorator", packageName + ".InjectAdapter",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("handle")
            .and(takesArgument(0, extendsClass(named("com.sun.jersey.api.client.ClientRequest"))))
            .and(returns(extendsClass(named("com.sun.jersey.api.client.ClientResponse")))),
        JaxRsClientV1Instrumentation.class.getName() + "$HandleAdvice");
  }

  public static class HandleAdvice {

    @Advice.OnMethodEnter
    public static SpanWithScope onEnter(
        @Advice.Argument(0) final ClientRequest request, @Advice.This final ClientHandler thisObj) {

      // WARNING: this might be a chain...so we only have to trace the first in the chain.
      boolean isRootClientHandler = null == request.getProperties().get(CONTEXT_ATTRIBUTE);
      if (isRootClientHandler) {
        Span span =
            TRACER
                .spanBuilder(DECORATE.spanNameForRequest(request))
                .setSpanKind(CLIENT)
                .startSpan();
        DECORATE.afterStart(span);
        DECORATE.onRequest(span, request);

        Context context = withSpan(span, Context.current());
        request.getProperties().put(CONTEXT_ATTRIBUTE, context);

        OpenTelemetry.getPropagators()
            .getHttpTextFormat()
            .inject(context, request.getHeaders(), SETTER);
        return new SpanWithScope(span, withScopedContext(context));
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Return final ClientResponse response,
        @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      Span span = spanWithScope.getSpan();
      DECORATE.onResponse(span, response);
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}
