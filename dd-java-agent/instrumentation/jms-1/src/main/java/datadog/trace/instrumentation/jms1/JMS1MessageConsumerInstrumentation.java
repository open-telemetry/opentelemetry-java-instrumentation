package datadog.trace.instrumentation.jms1;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static datadog.trace.instrumentation.jms.util.JmsUtil.toResourceName;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.jms.util.MessagePropertyTextMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JMS1MessageConsumerInstrumentation extends Instrumenter.Default {
  public static final String[] JMS1_HELPER_CLASS_NAMES =
      new String[] {
        "datadog.trace.instrumentation.jms.util.JmsUtil",
        "datadog.trace.instrumentation.jms.util.MessagePropertyTextMap"
      };

  public JMS1MessageConsumerInstrumentation() {
    super("jms", "jms-1");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.jms.MessageConsumer")));
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return not(classLoaderHasClasses("javax.jms.JMSContext", "javax.jms.CompletionListener"));
  }

  @Override
  public String[] helperClassNames() {
    return JMS1_HELPER_CLASS_NAMES;
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
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
              .withTag(DDTags.SERVICE_NAME, "jms")
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.MESSAGE_CONSUMER)
              .withTag(Tags.COMPONENT.getKey(), "jms1")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
              .withTag("span.origin.type", consumer.getClass().getName())
              .withStartTimestamp(TimeUnit.MILLISECONDS.toMicros(startTime));

      if (message == null) {
        spanBuilder = spanBuilder.withTag(DDTags.RESOURCE_NAME, "JMS " + method.getName());
      } else {
        spanBuilder =
            spanBuilder.withTag(
                DDTags.RESOURCE_NAME, "Consumed from " + toResourceName(message, null));

        final SpanContext extractedContext =
            GlobalTracer.get()
                .extract(Format.Builtin.TEXT_MAP, new MessagePropertyTextMap(message));
        if (extractedContext != null) {
          spanBuilder = spanBuilder.asChildOf(extractedContext);
        }
      }

      final Scope scope = spanBuilder.startActive(true);
      final Span span = scope.span();

      if (throwable != null) {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }

      scope.close();
    }
  }
}
