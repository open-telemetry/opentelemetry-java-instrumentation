/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaProducerMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void collectsProducerAndTopicMetrics() throws Exception {
    MBeanServer server = MBeanServerFactory.newMBeanServer();
    Map<String, Double> producerAttributes = new HashMap<>();
    producerAttributes.put("request-latency-avg", 250.0);
    producerAttributes.put("io-wait-time-ns-avg", 2_000_000_000.0);
    server.registerMBean(
        numericBean(producerAttributes),
        new ObjectName("kafka.producer:type=producer-metrics,client-id=example-producer"));
    server.registerMBean(
        numericBean(singletonMap("record-send-rate", 5.5)),
        new ObjectName(
            "kafka.producer:type=producer-topic-metrics,client-id=example-producer,topic=events"));

    cleanup.deferCleanup(
        JmxTelemetry.builder(testing.getOpenTelemetry())
            .setInternalMetricsSystemFilter(
                IncludeExclude.builder().setIncluded("kafka-producer").build())
            .setInternalMetricsUnstableMetricsFilter(
                IncludeExclude.builder().setIncluded("kafka.producer.*").build())
            .build()
            .start(() -> singletonList(server)));

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric ->
            metric
                .hasName("kafka.producer.io.wait.time.average")
                .hasUnit("s")
                .hasDoubleGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(2.0)
                                    .hasAttributes(
                                        Attributes.of(
                                            stringKey("messaging.client.id"),
                                            "example-producer")))),
        metric ->
            metric
                .hasName("kafka.producer.request.latency.average")
                .hasUnit("s")
                .hasDoubleGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(0.25)
                                    .hasAttributes(
                                        Attributes.of(
                                            stringKey("messaging.client.id"),
                                            "example-producer")))),
        metric ->
            metric
                .hasName("kafka.producer.topic.record.send.rate")
                .hasUnit("{record}/s")
                .hasDoubleGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(5.5)
                                    .hasAttributes(
                                        Attributes.of(
                                            stringKey("messaging.client.id"),
                                            "example-producer",
                                            stringKey("messaging.destination.name"),
                                            "events")))));
  }

  private static DynamicMBean numericBean(Map<String, Double> attributes) throws Exception {
    DynamicMBean bean = mock(DynamicMBean.class);
    List<MBeanAttributeInfo> attributeInfo = new ArrayList<>();
    for (Map.Entry<String, Double> attribute : attributes.entrySet()) {
      attributeInfo.add(
          new MBeanAttributeInfo(attribute.getKey(), "double", null, true, false, false));
      when(bean.getAttribute(attribute.getKey())).thenReturn(attribute.getValue());
    }
    when(bean.getMBeanInfo())
        .thenReturn(
            new MBeanInfo(
                DynamicMBean.class.getName(),
                null,
                attributeInfo.toArray(new MBeanAttributeInfo[0]),
                null,
                null,
                null));
    return bean;
  }
}
