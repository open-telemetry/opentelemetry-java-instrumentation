package datadog.trace.instrumentation.kafka_streams;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;

// This is necessary because SourceNodeRecordDeserializer drops the headers.  :-(
@AutoService(Instrumenter.class)
public class KafkaStreamsSourceNodeRecordDeserializerInstrumentation extends Instrumenter.Default {

  public KafkaStreamsSourceNodeRecordDeserializerInstrumentation() {
    super("kafka", "kafka-streams");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.streams.processor.internals.SourceNodeRecordDeserializer");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("deserialize"))
            .and(takesArgument(0, named("org.apache.kafka.clients.consumer.ConsumerRecord")))
            .and(returns(named("org.apache.kafka.clients.consumer.ConsumerRecord"))),
        SaveHeadersAdvice.class.getName());
  }

  public static class SaveHeadersAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void saveHeaders(
        @Advice.Argument(0) final ConsumerRecord incoming,
        @Advice.Return(readOnly = false) ConsumerRecord result) {
      result =
          new ConsumerRecord<>(
              result.topic(),
              result.partition(),
              result.offset(),
              result.timestamp(),
              TimestampType.CREATE_TIME,
              result.checksum(),
              result.serializedKeySize(),
              result.serializedValueSize(),
              result.key(),
              result.value(),
              incoming.headers());
    }
  }
}
