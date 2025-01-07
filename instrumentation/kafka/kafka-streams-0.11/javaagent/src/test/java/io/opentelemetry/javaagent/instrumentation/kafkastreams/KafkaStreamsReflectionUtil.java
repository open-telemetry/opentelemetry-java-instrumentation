/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.KStream;

/**
 * Kafka streams reflection util which is used to be compatible with different versions of kafka
 * streams.
 */
class KafkaStreamsReflectionUtil {

  private KafkaStreamsReflectionUtil() {}

  static class StreamBuilder {
    private final Object builder;

    StreamBuilder(Object builder) {
      this.builder = builder;
    }

    @SuppressWarnings("unchecked")
    KStream<Integer, String> stream(String topic)
        throws Exception { // Different api for test and latestDepTest.
      Method method;
      Object[] arguments;
      try {
        // equivalent to:
        // ((org.apache.kafka.streams.kstream.KStreamBuilder)builder).stream(STREAM_PENDING);
        method = builder.getClass().getMethod("stream", String[].class);
        String[] topics = new String[] {topic};
        arguments = new Object[] {topics};
      } catch (Exception exception) {
        // equivalent to:
        // ((org.apache.kafka.streams.StreamsBuilder)builder).stream(STREAM_PENDING);
        method = builder.getClass().getMethod("stream", String.class);
        arguments = new Object[] {topic};
      }

      return (KStream<Integer, String>) method.invoke(builder, arguments);
    }

    KafkaStreams createStreams(KStream<Integer, String> values, Properties config, String topic)
        throws Exception {
      Constructor<?> constructor;
      // Different api for test and latestDepTest.
      try {
        // equivalent to:
        //     values.to(Serdes.Integer(), Serdes.String(), STREAM_PROCESSED);
        //     return new KafkaStreams(builder, config);
        KStream.class
            .getMethod("to", Serde.class, Serde.class, String.class)
            .invoke(values, Serdes.Integer(), Serdes.String(), topic);

        Class<?> topologyBuilderClass =
            Class.forName("org.apache.kafka.streams.processor.TopologyBuilder");
        constructor = KafkaStreams.class.getConstructor(topologyBuilderClass, Properties.class);
      } catch (Exception exception) {
        constructor = null;
      }
      if (constructor != null) {
        return (KafkaStreams) constructor.newInstance(builder, config);
      }

      // equivalent to:
      //    Produced<Integer, String> produced = Produced.with(Serdes.Integer(), Serdes.String());
      //    values.to(STREAM_PROCESSED, produced);
      //
      //    Topology topology = builder.build();
      //    new KafkaStreams(topology, props);
      Class<?> producedClass = Class.forName("org.apache.kafka.streams.kstream.Produced");
      Method producedWith = producedClass.getMethod("with", Serde.class, Serde.class);
      Object producer = producedWith.invoke(null, Serdes.Integer(), Serdes.String());

      KStream.class.getMethod("to", String.class, producedClass).invoke(values, topic, producer);

      Object topology = builder.getClass().getMethod("build").invoke(builder);

      Class<?> topologyClass = Class.forName("org.apache.kafka.streams.Topology");
      constructor = KafkaStreams.class.getConstructor(topologyClass, Properties.class);

      return (KafkaStreams) constructor.newInstance(topology, config);
    }
  }

  static StreamBuilder createBuilder() throws Exception {
    Class<?> builderClass;
    try {
      // Different class names for test and latestDepTest.
      builderClass = Class.forName("org.apache.kafka.streams.kstream.KStreamBuilder");
    } catch (Exception e) {
      builderClass = Class.forName("org.apache.kafka.streams.StreamsBuilder");
    }
    return new StreamBuilder(builderClass.getConstructor().newInstance());
  }
}
