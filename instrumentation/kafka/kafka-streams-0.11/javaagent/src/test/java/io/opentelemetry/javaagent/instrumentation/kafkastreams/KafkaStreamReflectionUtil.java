/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.KStream;

/**
 * kafka stream reflection util which is used to compatible with different versions of kafka stream
 */
class KafkaStreamReflectionUtil {

  private KafkaStreamReflectionUtil() {}

  @SuppressWarnings("ClassNewInstance")
  static Object createBuilder()
      throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    try {
      // Different class names for test and latestDepTest.
      return Class.forName("org.apache.kafka.streams.kstream.KStreamBuilder").newInstance();
    } catch (ClassNotFoundException
        | NoClassDefFoundError
        | InstantiationException
        | IllegalAccessException e) {
      return Class.forName("org.apache.kafka.streams.StreamsBuilder").newInstance();
    }
  }

  @SuppressWarnings("unchecked")
  static KStream<Integer, String> stream(Object builder, String topic)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException {
    // Different api for test and latestDepTest.
    try {
      // equivalent to:
      // ((org.apache.kafka.streams.kstream.KStreamBuilder)builder).stream(STREAM_PENDING);
      return (KStream<Integer, String>)
          Class.forName("org.apache.kafka.streams.kstream.KStreamBuilder")
              .getMethod("stream", String[].class)
              .invoke(builder, topic);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException e) {
      // equivalent to:
      // ((org.apache.kafka.streams.StreamsBuilder)builder).stream(STREAM_PENDING);
      return (KStream<Integer, String>)
          Class.forName("org.apache.kafka.streams.StreamsBuilder")
              .getMethod("stream", String.class)
              .invoke(builder, topic);
    }
  }

  static KafkaStreams createStreams(
      Object builder, KStream<Integer, String> values, Properties config, String topic)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException,
          InstantiationException {
    // Different api for test and latestDepTest.
    try {
      // equivalent to:
      //     values.to(Serdes.Integer(), Serdes.String(), STREAM_PROCESSED);
      //     return new KafkaStreams(builder, config);
      Class<?> ksteamClass = Class.forName("org.apache.kafka.streams.kstream.KStream");
      ksteamClass
          .getMethod("to", Serde.class, Serde.class, String.class)
          .invoke(values, Serdes.Integer(), Serdes.String(), topic);

      Class<?> ksteamsClass = Class.forName("org.apache.kafka.streams.KStreams");
      Class<?> topologyBuilderClass =
          Class.forName("org.apache.kafka.streams.processor.TopologyBuilder");
      Constructor<?> constructor =
          ksteamsClass.getConstructor(topologyBuilderClass, Properties.class);

      return (KafkaStreams) constructor.newInstance(builder, config);
    } catch (NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException
        | ClassNotFoundException
        | InstantiationException e) {
      // equivalent to:
      //    Produced<Integer, String> produced = Produced.with(Serdes.Integer(), Serdes.String());
      //    values.to(STREAM_PROCESSED, produced);
      //
      //    Topology topology = builder.build();
      //    new KafkaStreams(topology, props);
      Class<?> producedClass = Class.forName("org.apache.kafka.streams.kstream.Produced");
      Method producedWith = producedClass.getMethod("with", Serde.class, Serde.class);
      Object producer = producedWith.invoke(null, Serdes.Integer(), Serdes.String());

      Class<?> ksteamClass = Class.forName("org.apache.kafka.streams.kstream.KStream");
      ksteamClass.getMethod("to", String.class, producedClass).invoke(values, topic, producer);

      Class<?> streamsBuilderClass = Class.forName("org.apache.kafka.streams.StreamsBuilder");
      Object topology = streamsBuilderClass.getMethod("build").invoke(builder);

      Class<?> ksteamsClass = Class.forName("org.apache.kafka.streams.KStreams");
      Class<?> topologyClass = Class.forName("org.apache.kafka.streams.Topology");
      Constructor<?> constructor = ksteamsClass.getConstructor(topologyClass, Properties.class);
      return (KafkaStreams) constructor.newInstance(topology, config);
    }
  }
}
