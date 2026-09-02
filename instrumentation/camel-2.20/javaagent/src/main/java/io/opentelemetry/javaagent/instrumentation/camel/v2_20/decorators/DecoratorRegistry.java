/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.javaagent.instrumentation.camel.v2_20.SpanDecorator;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.util.HashMap;
import java.util.Map;

public class DecoratorRegistry {

  private static final SpanDecorator defaultDecorator = new BaseSpanDecorator();
  private static final Map<String, SpanDecorator> decorators = loadDecorators();

  private static Map<String, SpanDecorator> loadDecorators() {
    Map<String, SpanDecorator> result = new HashMap<>();
    result.put("ahc", new HttpSpanDecorator());
    result.put("ampq", MessagingSpanDecorator.create("ampq", "amqp"));
    if (emitStableMessagingSemconv()) {
      result.put("amqp", MessagingSpanDecorator.create("amqp"));
    }
    result.put("aws-s3", new S3SpanDecorator());
    result.put("aws-sns", MessagingSpanDecorator.create("aws-sns", "aws.sns"));
    result.put("aws-sqs", MessagingSpanDecorator.create("aws-sqs", "aws_sqs", false));
    result.put("cometd", MessagingSpanDecorator.create("cometd"));
    result.put("cometds", MessagingSpanDecorator.create("cometds", "cometd"));
    result.put("cql", new DbSpanDecorator("cql", DbSystemNameIncubatingValues.CASSANDRA));
    result.put("direct", new InternalSpanDecorator());
    result.put("direct-vm", new InternalSpanDecorator());
    result.put("disruptor", new InternalSpanDecorator());
    result.put("disruptor-vm", new InternalSpanDecorator());
    result.put(
        "elasticsearch",
        new DbSpanDecorator("elasticsearch", DbSystemNameIncubatingValues.ELASTICSEARCH));
    result.put(
        "opensearch", new DbSpanDecorator("opensearch", DbSystemNameIncubatingValues.OPENSEARCH));
    result.put("http4", new Http4SpanDecorator());
    result.put("https4", new Https4SpanDecorator());
    result.put("http", new HttpSpanDecorator());
    result.put("ironmq", MessagingSpanDecorator.create("ironmq"));
    result.put("jdbc", new DbSpanDecorator("jdbc", DbSystemNameIncubatingValues.OTHER_SQL));
    result.put("jetty", new HttpSpanDecorator());
    result.put("jms", MessagingSpanDecorator.create("jms"));
    result.put("kafka", new KafkaSpanDecorator());
    result.put("log", new LogSpanDecorator());
    result.put("mongodb", new DbSpanDecorator("mongodb", DbSystemNameIncubatingValues.MONGODB));
    result.put("mqtt", MessagingSpanDecorator.create("mqtt", "mqtt", false));
    result.put("netty-http4", new HttpSpanDecorator());
    result.put("netty-http", new HttpSpanDecorator());
    result.put("paho", MessagingSpanDecorator.create("paho", "mqtt", false));
    result.put("rabbitmq", MessagingSpanDecorator.create("rabbitmq", "rabbitmq", true, "publish"));
    result.put("restlet", new HttpSpanDecorator());
    result.put("rest", new RestSpanDecorator());
    result.put("seda", new InternalSpanDecorator());
    result.put("servlet", new HttpSpanDecorator());
    result.put("sjms", MessagingSpanDecorator.create("sjms", "jms"));
    result.put("sql", new DbSpanDecorator("sql", DbSystemNameIncubatingValues.OTHER_SQL));
    result.put("stomp", MessagingSpanDecorator.create("stomp"));
    result.put("timer", new TimerSpanDecorator());
    result.put("undertow", new HttpSpanDecorator());
    result.put("vm", new InternalSpanDecorator());
    return result;
  }

  public SpanDecorator forComponent(String component) {

    return decorators.getOrDefault(component, defaultDecorator);
  }
}
