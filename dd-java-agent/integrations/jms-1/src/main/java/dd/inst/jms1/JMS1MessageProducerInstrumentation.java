package dd.inst.jms1;

import static com.datadoghq.agent.integration.JmsUtil.toResourceName;
import static dd.trace.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.datadoghq.agent.integration.MessagePropertyTextMap;
import com.datadoghq.trace.DDTags;
import com.google.auto.service.AutoService;
import dd.trace.DDAdvice;
import dd.trace.Instrumenter;
import io.opentracing.ActiveSpan;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class JMS1MessageProducerInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            not(isInterface()).and(hasSuperType(named("javax.jms.MessageProducer"))),
            not(classLoaderHasClasses("javax.jms.JMSContext", "javax.jms.CompletionListener")))
        .transform(
            DDAdvice.create()
                .advice(
                    named("send").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
                    ProducerAdvice.class.getName())
                .advice(
                    named("send")
                        .and(takesArgument(0, named("javax.jms.Destination")))
                        .and(takesArgument(1, named("javax.jms.Message")))
                        .and(isPublic()),
                    ProducerWithDestinationAdvice.class.getName()))
        .asDecorator();
  }

  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ActiveSpan startSpan(
        @Advice.Argument(0) final Message message, @Advice.This final MessageProducer producer) {
      Destination defaultDestination;
      try {
        defaultDestination = producer.getDestination();
      } catch (final JMSException e) {
        defaultDestination = null;
      }
      final ActiveSpan span =
          GlobalTracer.get()
              .buildSpan("jms.produce")
              .withTag(DDTags.SERVICE_NAME, "jms")
              .withTag(
                  DDTags.RESOURCE_NAME,
                  "Produced for " + toResourceName(message, defaultDestination))
              .withTag(Tags.COMPONENT.getKey(), "jms1")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER)
              .withTag("span.origin.type", producer.getClass().getName())
              .startActive();

      GlobalTracer.get()
          .inject(span.context(), Format.Builtin.TEXT_MAP, new MessagePropertyTextMap(message));

      return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final ActiveSpan span, @Advice.Thrown final Throwable throwable) {

      if (span != null) {
        if (throwable != null) {
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(Collections.singletonMap("error.object", throwable));
        }
        span.deactivate();
      }
    }
  }

  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ActiveSpan startSpan(
        @Advice.Argument(0) final Destination destination,
        @Advice.Argument(1) final Message message,
        @Advice.This final MessageProducer producer) {
      final ActiveSpan span =
          GlobalTracer.get()
              .buildSpan("jms.produce")
              .withTag(DDTags.SERVICE_NAME, "jms")
              .withTag(DDTags.RESOURCE_NAME, "Produced for " + toResourceName(message, destination))
              .withTag(Tags.COMPONENT.getKey(), "jms1")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER)
              .withTag("span.origin.type", producer.getClass().getName())
              .startActive();

      GlobalTracer.get()
          .inject(span.context(), Format.Builtin.TEXT_MAP, new MessagePropertyTextMap(message));
      return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final ActiveSpan span, @Advice.Thrown final Throwable throwable) {

      if (span != null) {
        if (throwable != null) {
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(Collections.singletonMap("error.object", throwable));
        }
        span.deactivate();
      }
    }
  }
}
