package dd.inst.jms1;

import static com.datadoghq.agent.integration.JmsUtil.toResourceName;
import static dd.trace.ClassLoaderMatcher.classLoaderHasClasses;
import static dd.trace.ExceptionHandlers.defaultExceptionHandler;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datadoghq.agent.integration.MessagePropertyTextMap;
import com.datadoghq.trace.DDTags;
import com.google.auto.service.AutoService;
import dd.trace.Instrumenter;
import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class JMS1MessageConsumerInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            not(isInterface()).and(hasSuperType(named("javax.jms.MessageConsumer"))),
            not(classLoaderHasClasses("javax.jms.JMSContext", "javax.jms.CompletionListener")))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    named("receive").and(takesArguments(0)).and(isPublic()),
                    ConsumerAdvice.class.getName())
                .advice(
                    named("receiveNoWait").and(takesArguments(0)).and(isPublic()),
                    ConsumerAdvice.class.getName())
                .withExceptionHandler(defaultExceptionHandler()))
        .asDecorator();
  }

  public static class ConsumerAdvice {

    @Advice.OnMethodEnter
    public static long startSpan() {
      return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final MessageConsumer consumer,
        @Advice.Enter final long startTime,
        @Advice.Return final Message message,
        @Advice.Thrown final Throwable throwable) {

      final SpanContext extractedContext =
          GlobalTracer.get().extract(Format.Builtin.TEXT_MAP, new MessagePropertyTextMap(message));

      final ActiveSpan span =
          GlobalTracer.get()
              .buildSpan("jms.consume")
              .asChildOf(extractedContext)
              .withTag(DDTags.SERVICE_NAME, "jms")
              .withTag(Tags.COMPONENT.getKey(), "jms1")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
              .withTag("span.origin.type", consumer.getClass().getName())
              .withStartTimestamp(TimeUnit.MILLISECONDS.toMicros(startTime))
              .startActive();

      if (throwable != null) {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap("error.object", throwable));
      }
      span.setTag(DDTags.RESOURCE_NAME, "Consumed from " + toResourceName(message, null));
      span.deactivate();
    }
  }
}
