package datadog.trace.instrumentation.aws;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.amazonaws.handlers.RequestHandler2;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.util.GlobalTracer;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class AWSClientInstrumentation extends Instrumenter.Configurable {

  public AWSClientInstrumentation() {
    super("aws-sdk");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            isAbstract()
                .and(
                    named("com.amazonaws.AmazonWebServiceClient")
                        .and(declaresField(named("requestHandler2s")))),
            classLoaderHasClasses(
                // aws classes used by opentracing contrib helpers
                "com.amazonaws.handlers.RequestHandler2",
                "com.amazonaws.Request",
                "com.amazonaws.Response",
                "com.amazonaws.handlers.HandlerContextKey"))
        .transform(
            new HelperInjector(
                "datadog.trace.instrumentation.aws.TracingRequestHandler",
                "datadog.trace.instrumentation.aws.SpanDecorator"))
        .transform(DDTransformers.defaultTransformers())
        .transform(DDAdvice.create().advice(isConstructor(), AWSClientAdvice.class.getName()))
        .asDecorator();
  }

  public static class AWSClientAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addHandler(
        @Advice.FieldValue("requestHandler2s") final List<RequestHandler2> handlers) {
      boolean hasDDHandler = false;
      for (final RequestHandler2 handler : handlers) {
        if (handler instanceof TracingRequestHandler) {
          hasDDHandler = true;
          break;
        }
      }
      if (!hasDDHandler) {
        handlers.add(new TracingRequestHandler(GlobalTracer.get()));
      }
    }
  }
}
