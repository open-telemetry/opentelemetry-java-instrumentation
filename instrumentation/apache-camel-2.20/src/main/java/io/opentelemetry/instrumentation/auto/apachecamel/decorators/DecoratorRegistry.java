/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.apachecamel.decorators;

import io.opentelemetry.instrumentation.auto.apachecamel.SpanDecorator;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import java.util.HashMap;
import java.util.Map;

public class DecoratorRegistry {

  private static final SpanDecorator DEFAULT = new BaseSpanDecorator();
  private static final Map<String, SpanDecorator> DECORATORS = loadDecorators();

  private static Map<String, SpanDecorator> loadDecorators() {

    Map<String, SpanDecorator> result = new HashMap<>();
    result.put("ahc", new HttpSpanDecorator());
    result.put("ampq", new MessagingSpanDecorator("ampq"));
    result.put("aws-sns", new MessagingSpanDecorator("aws-sns"));
    result.put("aws-sqs", new MessagingSpanDecorator("aws-sqs"));
    result.put("cometd", new MessagingSpanDecorator("cometd"));
    result.put("cometds", new MessagingSpanDecorator("cometds"));
    result.put("cql", new DbSpanDecorator("cql", DbSystem.CASSANDRA));
    result.put("direct", new InternalSpanDecorator());
    result.put("direct-vm", new InternalSpanDecorator());
    result.put("disruptor", new InternalSpanDecorator());
    result.put("disruptor-vm", new InternalSpanDecorator());
    result.put("elasticsearch", new DbSpanDecorator("elasticsearch", "elasticsearch"));
    result.put("http4", new HttpSpanDecorator());
    result.put("http", new HttpSpanDecorator());
    result.put("ironmq", new MessagingSpanDecorator("ironmq"));
    result.put("jdbc", new DbSpanDecorator("jdbc", DbSystem.OTHER_SQL));
    result.put("jetty", new HttpSpanDecorator());
    result.put("jms", new MessagingSpanDecorator("jms"));
    result.put("kafka", new KafkaSpanDecorator());
    result.put("log", new LogSpanDecorator());
    result.put("mongodb", new DbSpanDecorator("mongodb", DbSystem.MONGODB));
    result.put("mqtt", new MessagingSpanDecorator("mqtt"));
    result.put("netty-http4", new HttpSpanDecorator());
    result.put("netty-http", new HttpSpanDecorator());
    result.put("paho", new MessagingSpanDecorator("paho"));
    result.put("rabbitmq", new MessagingSpanDecorator("rabbitmq"));
    result.put("restlet", new HttpSpanDecorator());
    result.put("rest", new RestSpanDecorator());
    result.put("seda", new InternalSpanDecorator());
    result.put("servlet", new HttpSpanDecorator());
    result.put("sjms", new MessagingSpanDecorator("sjms"));
    result.put("sql", new DbSpanDecorator("sql", DbSystem.OTHER_SQL));
    result.put("stomp", new MessagingSpanDecorator("stomp"));
    result.put("timer", new TimerSpanDecorator());
    result.put("undertow", new HttpSpanDecorator());
    result.put("vm", new InternalSpanDecorator());
    return result;
  }

  public SpanDecorator forComponent(final String component) {

    return DECORATORS.getOrDefault(component, DEFAULT);
  }
}
