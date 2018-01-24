package datadog.trace.instrumentation.aws;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.contrib.aws.TracingRequestHandler;
import io.opentracing.util.GlobalTracer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class AWSClientInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            hasSuperType(named("com.amazonaws.client.builder.AwsClientBuilder")),
            classLoaderHasClasses(
                // aws classes used by opentracing contrib helpers
                "com.amazonaws.handlers.RequestHandler2",
                "com.amazonaws.Request",
                "com.amazonaws.Response",
                "com.amazonaws.handlers.HandlerContextKey"))
        .transform(
            new HelperInjector(
                "io.opentracing.contrib.aws.TracingRequestHandler",
                "io.opentracing.contrib.aws.SpanDecorator"))
        .transform(
            DDAdvice.create()
                .advice(
                    named("build").and(takesArguments(0)).and(isPublic()).and(not(isAbstract())),
                    AWSClientAdvice.class.getName()))
        .asDecorator();
  }

  public static class AWSClientAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(@Advice.This final AwsClientBuilder<?, ?> builder) {
      List<RequestHandler2> handlers = builder.getRequestHandlers();
      boolean hasDDHandler = false;
      if (null == handlers) {
        handlers = new ArrayList<RequestHandler2>(1);
      } else {
        for (RequestHandler2 handler : handlers) {
          if (handler instanceof TracingRequestHandler) {
            hasDDHandler = true;
            break;
          }
        }
      }
      if (!hasDDHandler) {
        handlers.add(new TracingRequestHandler(GlobalTracer.get()));
        builder.setRequestHandlers(handlers.toArray(new RequestHandler2[0]));
      }
    }
  }
}
