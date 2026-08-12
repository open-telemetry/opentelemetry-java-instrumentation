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
    registerMessaging(result, "ampq");
    if (emitStableMessagingSemconv()) {
      registerMessaging(result, "amqp");
    }
    result.put("aws-s3", new S3SpanDecorator());
    registerMessaging(result, "aws-sns", "aws.sns");
    registerMessaging(result, "aws-sqs", "aws_sqs", false);
    registerMessaging(result, "cometd");
    registerMessaging(result, "cometds", "cometd");
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
    registerMessaging(result, "ironmq");
    result.put("jdbc", new DbSpanDecorator("jdbc", DbSystemNameIncubatingValues.OTHER_SQL));
    result.put("jetty", new HttpSpanDecorator());
    registerMessaging(result, "jms");
    result.put("kafka", new KafkaSpanDecorator());
    result.put("log", new LogSpanDecorator());
    result.put("mongodb", new DbSpanDecorator("mongodb", DbSystemNameIncubatingValues.MONGODB));
    registerMessaging(result, "mqtt");
    result.put("netty-http4", new HttpSpanDecorator());
    result.put("netty-http", new HttpSpanDecorator());
    registerMessaging(result, "paho", "mqtt");
    registerMessaging(result, "rabbitmq");
    result.put("restlet", new HttpSpanDecorator());
    result.put("rest", new RestSpanDecorator());
    result.put("seda", new InternalSpanDecorator());
    result.put("servlet", new HttpSpanDecorator());
    registerMessaging(result, "sjms", "jms");
    result.put("sql", new DbSpanDecorator("sql", DbSystemNameIncubatingValues.OTHER_SQL));
    registerMessaging(result, "stomp");
    result.put("timer", new TimerSpanDecorator());
    result.put("undertow", new HttpSpanDecorator());
    result.put("vm", new InternalSpanDecorator());
    return result;
  }

  private static void registerMessaging(
      Map<String, SpanDecorator> decorators,
      String component,
      String system,
      boolean spanContextPropagated) {
    decorators.put(component, new MessagingSpanDecorator(component, system, spanContextPropagated));
  }

  private static void registerMessaging(
      Map<String, SpanDecorator> decorators, String component, String system) {
    registerMessaging(decorators, component, system, true);
  }

  private static void registerMessaging(Map<String, SpanDecorator> decorators, String component) {
    registerMessaging(decorators, component, component);
  }

  public SpanDecorator forComponent(String component) {

    return decorators.getOrDefault(component, defaultDecorator);
  }
}
