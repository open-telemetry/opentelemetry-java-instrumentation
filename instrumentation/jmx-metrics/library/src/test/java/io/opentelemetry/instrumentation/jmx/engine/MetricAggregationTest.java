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
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.assertj.core.api.Assertions;
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
  void after() throws Exception {
    ObjectName objectName = new ObjectName(
        "otel.jmx.test:type=" + Hello.class.getSimpleName() + ",*");
    theServer.queryMBeans(objectName, null).forEach(
        instance -> {
          try {
            theServer.unregisterMBean(instance.getObjectName());
          } catch (InstanceNotFoundException | MBeanRegistrationException e) {
            throw new RuntimeException(e);
          }
        }
    );

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

    Collection<MetricData> data = testMetric(bean.toString(), Collections.emptyList());
    checkSingleValue(data, 42);
  }

  @Test
  void aggregateOneParam() throws Exception {
    theServer.registerMBean(new Hello(42), getObjectName("value1", null));
    theServer.registerMBean(new Hello(37), getObjectName("value2", null));

    String bean = getObjectName("*", null).toString();
    Collection<MetricData> data = testMetric(bean, Collections.emptyList());
    checkSingleValue(data, 79);
  }

  @Test
  void aggregateMultipleParams() throws Exception {
    theServer.registerMBean(new Hello(1), getObjectName("1", "x"));
    theServer.registerMBean(new Hello(2), getObjectName("2", "y"));
    theServer.registerMBean(new Hello(3), getObjectName("3", "x"));
    theServer.registerMBean(new Hello(4), getObjectName("4", "y"));

    String bean = getObjectName("*", "*").toString();
    Collection<MetricData> data = testMetric(bean, Collections.emptyList());
    checkSingleValue(data, 10);
  }

  @Test
  void partialAggregateMultipleParams() throws Exception {
    theServer.registerMBean(new Hello(1), getObjectName("1", "x"));
    theServer.registerMBean(new Hello(2), getObjectName("2", "y"));
    theServer.registerMBean(new Hello(3), getObjectName("3", "x"));
    theServer.registerMBean(new Hello(4), getObjectName("4", "y"));

    String bean = getObjectName("*", "*").toString();

    List<MetricAttribute> attributes = Collections.singletonList(
        new MetricAttribute("test.metric.param",
            MetricAttributeExtractor.fromObjectNameParameter("b")));
    Collection<MetricData> data = testMetric(bean, attributes);

    assertThat(data)
        .isNotEmpty()
        .satisfiesExactlyInAnyOrder(
            metric -> {
              assertThat(metric.getName()).isEqualTo("test.metric");
              Collection<LongPointData> points = metric.getLongSumData().getPoints();
              assertThat(points).hasSize(1);
              LongPointData pointData = points.stream().findFirst().get();
              assertThat(pointData.getAttributes()).containsEntry("b", "y");
              assertThat(pointData.getValue()).isEqualTo(6);
            }, metric -> {
              Assertions.assertThat(metric.getName()).isEqualTo("test.metric");
              Collection<LongPointData> points = metric.getLongSumData().getPoints();
              assertThat(points).hasSize(1);
              LongPointData pointData = points.stream().findFirst().get();
              assertThat(pointData.getAttributes()).containsEntry("b", "x");
              assertThat(pointData.getValue()).isEqualTo(4);
            });
  }

  private Collection<MetricData> testMetric(String mbean, List<MetricAttribute> attributes)
      throws MalformedObjectNameException {
    JmxMetricInsight metricInsight = JmxMetricInsight.createService(sdk, 0);
    MetricConfiguration metricConfiguration = new MetricConfiguration();
    List<MetricExtractor> extractors = new ArrayList<>();

    MetricInfo metricInfo =
        new MetricInfo("test.metric", "description", null, "1", MetricInfo.Type.COUNTER);
    BeanAttributeExtractor beanExtractor = BeanAttributeExtractor.fromName("Value");
    MetricExtractor extractor = new MetricExtractor(beanExtractor, metricInfo, attributes);
    extractors.add(extractor);
    MetricDef metricDef =
        new MetricDef(BeanGroup.forBeans(Collections.singletonList(mbean)), extractors);

    metricConfiguration.addMetricDef(metricDef);
    metricInsight.startLocal(metricConfiguration);

    return waitMetricsReceived();
  }

  private static void checkSingleValue(Collection<MetricData> data, int expectedValue) {
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
