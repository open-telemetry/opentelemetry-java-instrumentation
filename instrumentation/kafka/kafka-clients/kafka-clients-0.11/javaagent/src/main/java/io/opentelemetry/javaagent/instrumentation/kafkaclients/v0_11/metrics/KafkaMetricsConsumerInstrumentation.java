/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.metrics;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.metrics.KafkaMetricsUtil.enhanceConfig;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class KafkaMetricsConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.consumer.KafkaConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, Map.class)),
        this.getClass().getName() + "$ConstructorMapAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, Properties.class)),
        this.getClass().getName() + "$ConstructorPropertiesAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorMapAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) Map<String, Object> config) {

      // In versions of spring-kafka prior to 2.5.0.RC1, when the `ProducerPerThread`
      //  of DefaultKafkaProducerFactory is set to true, the `config` object entering
      //  this advice block can be shared across multiple threads. Directly modifying
      //  `config` could lead to unexpected item loss due to race conditions, where
      //  some entries might be lost as different threads attempt to modify it
      //  concurrently.
      //
      // To prevent such issues, a copy of the `config` should be created here before
      //  any modifications are made. This ensures that each thread operates on its
      //  own independent copy of the configuration, thereby eliminating the risk of
      //  configurations corruption.
      //
      // More detailed information:
      //  https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/12538

      // ensure config is a mutable map and avoid concurrency conflicts
      config = new HashMap<>(config);
      enhanceConfig(config);
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorPropertiesAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) Properties config) {
      enhanceConfig(config);
    }
  }
}
