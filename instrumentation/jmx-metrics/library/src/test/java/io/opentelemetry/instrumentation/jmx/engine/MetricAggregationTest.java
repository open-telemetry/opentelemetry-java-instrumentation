/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MetricAggregationTest {

  @SuppressWarnings("unused")
  public interface HelloMBean {

    int getValue();
  }

  public static class Hello implements HelloMBean {

    private final int value;

    public Hello(int value) {
      this.value = value;
    }

    @Override
    public int getValue() {
      return value;
    }
  }

  private static final String DOMAIN = "otel.jmx.test";
  private static MBeanServer theServer;

  @BeforeAll
  static void setUp() {
    theServer = MBeanServerFactory.createMBeanServer(DOMAIN);
  }

  @AfterAll
  static void tearDown() {
    MBeanServerFactory.releaseMBeanServer(theServer);
  }

  @BeforeEach
  void before() {
    reader = InMemoryMetricReader.createDelta();
    sdk =
        OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build())
            .build();
  }

  @AfterEach
  void after() {
    if (sdk != null) {
      sdk.getSdkMeterProvider().close();
    }
  }

  private InMemoryMetricReader reader;
  private OpenTelemetrySdk sdk;

  private static ObjectName getObjectName(@Nullable String a, @Nullable String b) {
    StringBuilder parts = new StringBuilder();
    parts.append("otel.jmx.test:type=").append(Hello.class.getSimpleName());
    if (a != null) {
      parts.append(",a=").append(a);
    }
    if (b != null) {
      parts.append(",b=").append(b);
    }
    try {
      return new ObjectName(parts.toString());
    } catch (MalformedObjectNameException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void singleInstance() throws Exception {
    ObjectName bean = getObjectName(null, null);
    theServer.registerMBean(new Hello(42), bean);

    testMetric(bean.toString(), 42);
  }

  @Test
  void multipleInstancesAggregated_twoInstances() throws Exception {
    theServer.registerMBean(new Hello(42), getObjectName("value1", null));
    theServer.registerMBean(new Hello(37), getObjectName("value2", null));

    String bean = getObjectName("*", null).toString();
    testMetric(bean, 79);
  }

  @Test
  void multipleInstancesAggregated_fourInstances() throws Exception {
    theServer.registerMBean(new Hello(1), getObjectName("1", "a"));
    theServer.registerMBean(new Hello(2), getObjectName("2", "b"));
    theServer.registerMBean(new Hello(3), getObjectName("3", "a"));
    theServer.registerMBean(new Hello(5), getObjectName("4", "b"));

    String bean = getObjectName("*", "*").toString();
    testMetric(bean, 11);
  }

  void testMetric(String mbean, int expectedValue) throws MalformedObjectNameException {
    JmxMetricInsight metricInsight = JmxMetricInsight.createService(sdk, 0);
    MetricConfiguration metricConfiguration = new MetricConfiguration();
    List<MetricExtractor> extractors = new ArrayList<>();

    MetricInfo metricInfo =
        new MetricInfo("test.metric", "description", null, "1", MetricInfo.Type.COUNTER);
    BeanAttributeExtractor beanExtractor = BeanAttributeExtractor.fromName("Value");
    MetricExtractor extractor =
        new MetricExtractor(beanExtractor, metricInfo, Collections.emptyList());
    extractors.add(extractor);
    MetricDef metricDef =
        new MetricDef(BeanGroup.forBeans(Collections.singletonList(mbean)), extractors);

    metricConfiguration.addMetricDef(metricDef);
    metricInsight.startLocal(metricConfiguration);

    Collection<MetricData> data = waitMetricsReceived();

    assertThat(data)
        .isNotEmpty()
        .satisfiesExactlyInAnyOrder(
            metric -> {
              assertThat(metric.getName()).isEqualTo("test.metric");
              Collection<LongPointData> points = metric.getLongSumData().getPoints();
              assertThat(points).hasSize(1);
              LongPointData pointData = points.stream().findFirst().get();
              assertThat(pointData.getValue()).isEqualTo(expectedValue);
              assertThat(pointData.getAttributes()).isEmpty();
            });
  }

  private Collection<MetricData> waitMetricsReceived() {
    int retries = 100;
    Collection<MetricData> data;
    do {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      data = reader.collectAllMetrics();
    } while (data.isEmpty() && retries-- > 0);
    if (data.isEmpty()) {
      throw new RuntimeException("timed out waiting for metrics received");
    }
    return data;
  }
}
