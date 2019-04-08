package datadog.trace.instrumentation.jms;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.jms.JMSDecorator.CONSUMER_DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JMSMessageConsumerInstrumentation extends Instrumenter.Default {

  public JMSMessageConsumerInstrumentation() {
    super("jms", "jms-1", "jms-2");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.jms.MessageConsumer")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      packageName + ".JMSDecorator",
      packageName + ".JMSDecorator$1",
      packageName + ".JMSDecorator$2",
      packageName + ".MessagePropertyTextMap",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("receive").and(takesArguments(0).or(takesArguments(1))).and(isPublic()),
        ConsumerAdvice.class.getName());
    transformers.put(
        named("receiveNoWait").and(takesArguments(0)).and(isPublic()),
        ConsumerAdvice.class.getName());
    return transformers;
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
        @Advice.Origin final Method method,
        @Advice.Return final Message message,
        @Advice.Thrown final Throwable throwable) {
      Tracer.SpanBuilder spanBuilder =
          GlobalTracer.get()
              .buildSpan("jms.consume")
              .withTag("span.origin.type", consumer.getClass().getName())
              .withStartTimestamp(TimeUnit.MILLISECONDS.toMicros(startTime));

      if (message != null) {
        final SpanContext extractedContext =
            GlobalTracer.get()
                .extract(Format.Builtin.TEXT_MAP, new MessagePropertyTextMap(message));
        if (extractedContext != null) {
          spanBuilder = spanBuilder.asChildOf(extractedContext);
        }
      }

      final Span span = spanBuilder.start();
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
        CONSUMER_DECORATE.afterStart(span);
        if (message == null) {
          CONSUMER_DECORATE.onReceive(span, method);
        } else {
          CONSUMER_DECORATE.onConsume(span, message);
        }
        CONSUMER_DECORATE.onError(span, throwable);
        CONSUMER_DECORATE.beforeFinish(span);
        span.finish();
      }
    }
  }
}
