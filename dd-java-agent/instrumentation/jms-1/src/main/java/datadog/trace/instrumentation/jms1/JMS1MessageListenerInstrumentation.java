package datadog.trace.instrumentation.jms1;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static datadog.trace.instrumentation.jms.util.JmsUtil.toResourceName;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.jms.util.MessagePropertyTextMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Message;
import javax.jms.MessageListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JMS1MessageListenerInstrumentation extends Instrumenter.Default {

  public JMS1MessageListenerInstrumentation() {
    super("jms", "jms-1");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.jms.MessageListener")));
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return not(classLoaderHasClasses("javax.jms.JMSContext", "javax.jms.CompletionListener"));
  }

  @Override
  public String[] helperClassNames() {
    return JMS1MessageConsumerInstrumentation.JMS1_HELPER_CLASS_NAMES;
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("onMessage").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        MessageListenerAdvice.class.getName());
    return transformers;
  }

  public static class MessageListenerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(0) final Message message, @Advice.This final MessageListener listener) {

      final SpanContext extractedContext =
          GlobalTracer.get().extract(Format.Builtin.TEXT_MAP, new MessagePropertyTextMap(message));

      final Scope scope =
          GlobalTracer.get()
              .buildSpan("jms.onMessage")
              .asChildOf(extractedContext)
              .withTag(DDTags.SERVICE_NAME, "jms")
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.MESSAGE_CONSUMER)
              .withTag(DDTags.RESOURCE_NAME, "Received from " + toResourceName(message, null))
              .withTag(Tags.COMPONENT.getKey(), "jms1")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
              .withTag("span.origin.type", listener.getClass().getName())
              .startActive(true);

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {

      if (scope != null) {
        if (throwable != null) {
          final Span span = scope.span();
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        }
        scope.close();
      }
    }
  }
}
