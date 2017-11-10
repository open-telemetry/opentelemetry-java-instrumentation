package dd.inst.aws;

import static dd.trace.ClassLoaderMatcher.classLoaderHasClasses;
import static dd.trace.ExceptionHandlers.defaultExceptionHandler;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.google.auto.service.AutoService;
import dd.trace.Instrumenter;
import io.opentracing.contrib.aws.TracingRequestHandler;
import io.opentracing.util.GlobalTracer;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class AWSClientInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("com.amazonaws.client.builder.AwsSyncClientBuilder")
                .or(named("com.amazonaws.client.builder.AwsAsyncClientBuilder")),
            classLoaderHasClasses(
                "com.amazonaws.http.client.HttpClientFactory",
                "com.amazonaws.http.apache.utils.ApacheUtils"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    named("build").and(takesArguments(0)).and(isPublic()),
                    AWSClientAdvice.class.getName())
                .withExceptionHandler(defaultExceptionHandler()))
        .asDecorator();
  }

  public static class AWSClientAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(@Advice.This final AwsClientBuilder builder) {

      final RequestHandler2 handler = new TracingRequestHandler(GlobalTracer.get());

      List<RequestHandler2> handlers = builder.getRequestHandlers();

      if (handlers == null || handlers.isEmpty()) {
        handlers = Arrays.asList(handler);
      } else {
        // Check if we already added the handler
        if (!(handlers.get(0) instanceof TracingRequestHandler)) {
          handlers.add(0, handler);
        }
      }
      builder.setRequestHandlers(handler);
    }
  }
}
