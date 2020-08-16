/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.kafkastreams;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
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
        KafkaStreamsSourceNodeRecordDeserializerInstrumentation.class.getName()
            + "$SaveHeadersAdvice");
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
