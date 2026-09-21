/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.TestKafkaConsumerFactory;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaCamelPollInstrumentationTest {

  private static final boolean CAMEL_DISABLED = Boolean.getBoolean("testCamelDisabled");
  private static final boolean ADAPTER_DISABLED = Boolean.getBoolean("testAdapterDisabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void selectsOnlyCamelProcessingAfterKafkaPoll(boolean camelConsumer) throws Exception {
    ConsumerRecord<String, String> record = new ConsumerRecord<>("test", 0, 0, "key", "value");
    KafkaConsumer<String, String> consumer = TestKafkaConsumerFactory.create(record);
    ClassLoader classLoader = consumer.getClass().getClassLoader();
    if (Boolean.getBoolean("otel.javaagent.experimental.indy")
        || Boolean.getBoolean("otel.instrumentation.common.v3-preview")) {
      Class<?> registry =
          AgentClassLoaderAccess.loadClass(
              "io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry");
      classLoader =
          (ClassLoader)
              registry
                  .getMethod("getInstrumentationClassLoader", String.class, ClassLoader.class)
                  .invoke(
                      null,
                      "io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11"
                          + ".KafkaClientsInstrumentationModule",
                      classLoader);
    }
    if (camelConsumer && !CAMEL_DISABLED && !ADAPTER_DISABLED) {
      Class<?> camelSelection =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelKafkaProcessingSelection",
              true,
              classLoader);
      camelSelection.getMethod("markConsumer", KafkaConsumer.class).invoke(null, consumer);
    }

    ConsumerRecords<String, String> records = consumer.poll(0);

    Class<?> kafkaSelection =
        Class.forName(
            "io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11"
                + ".KafkaProcessingSelectionUtil",
            true,
            classLoader);
    Method rawBatchSelection =
        kafkaSelection.getMethod(
            "rawProcessingSelection", ConsumerRecords.class, BooleanSupplier.class);
    BooleanSupplier batchSelection =
        (BooleanSupplier) rawBatchSelection.invoke(null, records, (BooleanSupplier) () -> true);

    Class<?> kafkaContext =
        Class.forName(
            "io.opentelemetry.javaagent.shaded.instrumentation.kafkaclients.common.v0_11.internal"
                + ".KafkaConsumerContextUtil",
            true,
            classLoader);
    Method rawRecordSelection =
        kafkaContext.getMethod("getRawProcessingSelection", ConsumerRecord.class);

    assertThat(records.count()).isEqualTo(1);
    assertThat(batchSelection.getAsBoolean())
        .isEqualTo(!camelConsumer || CAMEL_DISABLED || ADAPTER_DISABLED);
    assertThat(rawRecordSelection.invoke(null, record))
        .isInstanceOfSatisfying(
            KafkaConsumerBatchState.class,
            recordSelection ->
                assertThat(recordSelection.getAsBoolean())
                    .isEqualTo(!camelConsumer || CAMEL_DISABLED || ADAPTER_DISABLED));
  }
}
