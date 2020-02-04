package io.opentelemetry.auto.instrumentation.jaxrs.v1;

import static io.opentelemetry.auto.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.jaxrs.v1.InjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.jaxrs.v1.JaxRsClientV1Decorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jaxrs.v1.JaxRsClientV1Decorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
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
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("com.sun.jersey.api.client.ClientHandler"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".JaxRsClientV1Decorator",
      packageName + ".InjectAdapter",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("handle")
            .and(
                takesArgument(
                    0, safeHasSuperType(named("com.sun.jersey.api.client.ClientRequest"))))
            .and(returns(safeHasSuperType(named("com.sun.jersey.api.client.ClientResponse")))),
        JaxRsClientV1Instrumentation.class.getName() + "$HandleAdvice");
  }

  public static class HandleAdvice {

    @Advice.OnMethodEnter
    public static SpanWithScope onEnter(
        @Advice.Argument(value = 0) final ClientRequest request,
        @Advice.This final ClientHandler thisObj) {

      // WARNING: this might be a chain...so we only have to trace the first in the chain.
      final boolean isRootClientHandler = null == request.getProperties().get(SPAN_ATTRIBUTE);
      if (isRootClientHandler) {
        final Span span = TRACER.spanBuilder("jax-rs.client.call").startSpan();
        DECORATE.afterStart(span);
        DECORATE.onRequest(span, request);
        request.getProperties().put(SPAN_ATTRIBUTE, span);

        TRACER.getHttpTextFormat().inject(span.getContext(), request.getHeaders(), SETTER);
        return new SpanWithScope(span, TRACER.withSpan(span));
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter final SpanWithScope spanAndScope,
        @Advice.Return final ClientResponse response,
        @Advice.Thrown final Throwable throwable) {
      if (spanAndScope == null) {
        return;
      }
      final Span span = spanAndScope.getSpan();
      DECORATE.onResponse(span, response);
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanAndScope.closeScope();
    }
  }
}
