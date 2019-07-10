package datadog.trace.instrumentation.jaxrs.v1;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.jaxrs.v1.JaxRsClientV1Decorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
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
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
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
        HandleAdvice.class.getName());
  }

  public static class HandleAdvice {

    @Advice.OnMethodEnter
    public static Scope onEnter(
        @Advice.Argument(value = 0) final ClientRequest request,
        @Advice.This final ClientHandler thisObj) {

      // WARNING: this might be a chain...so we only have to trace the first in the chain.
      final boolean isRootClientHandler = null == request.getProperties().get("dd.span");
      if (isRootClientHandler) {
        final Tracer tracer = GlobalTracer.get();
        final Span span =
            tracer
                .buildSpan("jax-rs.client.call")
                .withTag(DDTags.RESOURCE_NAME, request.getMethod() + " jax-rs.client.call")
                .start();
        DECORATE.afterStart(span);
        DECORATE.onRequest(span, request);
        request.getProperties().put("dd.span", span);

        tracer.inject(
            span.context(), Format.Builtin.HTTP_HEADERS, new InjectAdapter(request.getHeaders()));
        return tracer.scopeManager().activate(span, true);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter final Scope scope,
        @Advice.Return final ClientResponse response,
        @Advice.Thrown final Throwable throwable) {
      if (null != scope) {
        final Span span = scope.span();
        DECORATE.onResponse(span, response);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        scope.close();
      }
    }
  }
}
