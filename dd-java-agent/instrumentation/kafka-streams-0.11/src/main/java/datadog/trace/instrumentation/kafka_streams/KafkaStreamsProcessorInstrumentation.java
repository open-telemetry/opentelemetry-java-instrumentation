package datadog.trace.instrumentation.kafka_streams;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsProcessorInstrumentation {
  // These two instrumentations work together to apply StreamTask.process.
  // The combination of these are needed because there's not a good instrumentation point.

  public static final String[] HELPER_CLASS_NAMES =
      new String[] {"datadog.trace.instrumentation.kafka_streams.TextMapExtractAdapter"};

  @AutoService(Instrumenter.class)
  public static class StartInstrumentation extends Instrumenter.Default {

    public StartInstrumentation() {
      super("kafka", "kafka-streams");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.kafka.streams.processor.internals.PartitionGroup");
    }

    @Override
    public String[] helperClassNames() {
      return HELPER_CLASS_NAMES;
    }

    @Override
    public Map<ElementMatcher, String> transformers() {
      final Map<ElementMatcher, String> transformers = new HashMap<>();
      transformers.put(
          isMethod()
              .and(isPackagePrivate())
              .and(named("nextRecord"))
              .and(returns(named("org.apache.kafka.streams.processor.internals.StampedRecord"))),
          StartSpanAdvice.class.getName());
      return transformers;
    }

    public static class StartSpanAdvice {

      @Advice.OnMethodExit(suppress = Throwable.class)
      public static void startSpan(@Advice.Return final StampedRecord record) {
        if (record == null) {
          return;
        }

        final SpanContext extractedContext =
            GlobalTracer.get()
                .extract(
                    Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(record.value.headers()));

        GlobalTracer.get()
            .buildSpan("kafka.consume")
            .asChildOf(extractedContext)
            .withTag(DDTags.SERVICE_NAME, "kafka")
            .withTag(DDTags.RESOURCE_NAME, "Consume Topic " + record.topic())
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.MESSAGE_CONSUMER)
            .withTag(Tags.COMPONENT.getKey(), "java-kafka")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
            .withTag("partition", record.partition())
            .withTag("offset", record.offset())
            .startActive(true);
      }
    }
  }

  @AutoService(Instrumenter.class)
  public static class StopInstrumentation extends Instrumenter.Default {

    public StopInstrumentation() {
      super("kafka", "kafka-streams");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.kafka.streams.processor.internals.StreamTask");
    }

    @Override
    public String[] helperClassNames() {
      return HELPER_CLASS_NAMES;
    }

    @Override
    public Map<ElementMatcher, String> transformers() {
      final Map<ElementMatcher, String> transformers = new HashMap<>();
      transformers.put(
          isMethod().and(isPublic()).and(named("process")).and(takesArguments(0)),
          StopSpanAdvice.class.getName());
      return transformers;
    }

    public static class StopSpanAdvice {

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void stopSpan(@Advice.Thrown final Throwable throwable) {
        final Scope scope = GlobalTracer.get().scopeManager().active();
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
}
