/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.params.ParameterizedInvocationConstants.ARGUMENTS_PLACEHOLDER;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class MetricAggregationTest {

  @SuppressWarnings({"unused", "checkstyle:AbbreviationAsWordInName"})
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
    ObjectName objectName =
        new ObjectName("otel.jmx.test:type=" + Hello.class.getSimpleName() + ",*");
    theServer
        .queryMBeans(objectName, null)
        .forEach(
            instance -> {
              try {
                theServer.unregisterMBean(instance.getObjectName());
              } catch (InstanceNotFoundException | MBeanRegistrationException e) {
                throw new RuntimeException(e);
              }
            });

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

  static List<MetricInfo.Type> metricTypes() {
    return Arrays.asList(
        MetricInfo.Type.COUNTER, MetricInfo.Type.UPDOWNCOUNTER, MetricInfo.Type.GAUGE);
  }

  @ParameterizedTest(name = ARGUMENTS_PLACEHOLDER)
  @MethodSource("metricTypes")
  void singleInstance(MetricInfo.Type metricType) throws Exception {
    ObjectName bean = getObjectName(null, null);
    theServer.registerMBean(new Hello(42), bean);

    Collection<MetricData> data = testMetric(bean.toString(), Collections.emptyList(), metricType);
    checkSingleValue(data, 42, metricType);
  }

  @ParameterizedTest(name = ARGUMENTS_PLACEHOLDER)
  @MethodSource("metricTypes")
  void aggregateOneParam(MetricInfo.Type metricType) throws Exception {
    theServer.registerMBean(new Hello(42), getObjectName("value1", null));
    theServer.registerMBean(new Hello(37), getObjectName("value2", null));

    String bean = getObjectName("*", null).toString();
    Collection<MetricData> data = testMetric(bean, Collections.emptyList(), metricType);
    int expected = 79;
    if (metricType == MetricInfo.Type.GAUGE) {
      // last-value aggregation produces unpredictable result unless a single mbean instance is used
      // test here is only used as a way to document behavior and should not be considered a feature
      expected = 37;
    }
    checkSingleValue(data, expected, metricType);
  }

  @ParameterizedTest(name = ARGUMENTS_PLACEHOLDER)
  @MethodSource("metricTypes")
  void aggregateMultipleParams(MetricInfo.Type metricType) throws Exception {
    theServer.registerMBean(new Hello(1), getObjectName("1", "x"));
    theServer.registerMBean(new Hello(2), getObjectName("2", "y"));
    theServer.registerMBean(new Hello(3), getObjectName("3", "x"));
    theServer.registerMBean(new Hello(4), getObjectName("4", "y"));

    String bean = getObjectName("*", "*").toString();
    Collection<MetricData> data = testMetric(bean, Collections.emptyList(), metricType);

    int expected = 10;
    if (metricType == MetricInfo.Type.GAUGE) {
      // last-value aggregation produces unpredictable result unless a single mbean instance is used
      // test here is only used as a way to document behavior and should not be considered a feature
      expected = 1;
    }
    checkSingleValue(data, expected, metricType);
  }

  @ParameterizedTest(name = ARGUMENTS_PLACEHOLDER)
  @MethodSource("metricTypes")
  void partialAggregateMultipleParams(MetricInfo.Type metricType) throws Exception {
    theServer.registerMBean(new Hello(1), getObjectName("1", "x"));
    theServer.registerMBean(new Hello(2), getObjectName("2", "y"));
    theServer.registerMBean(new Hello(3), getObjectName("3", "x"));
    theServer.registerMBean(new Hello(4), getObjectName("4", "y"));

    String bean = getObjectName("*", "*").toString();

    List<MetricAttribute> attributes =
        Collections.singletonList(
            new MetricAttribute(
                "test.metric.param", MetricAttributeExtractor.fromObjectNameParameter("b")));
    Collection<MetricData> data = testMetric(bean, attributes, metricType);

    assertThat(data)
        .isNotEmpty()
        .satisfiesExactly(
            metric -> {
              assertThat(metric.getName()).isEqualTo("test.metric");
              AttributeKey<String> metricAttribute = AttributeKey.stringKey("test.metric.param");

              if (metricType == MetricInfo.Type.GAUGE) {
                // last-value aggregation produces unpredictable result unless a single mbean
                // instance is used
                // test here is only used as a way to document behavior and should not be considered
                // a feature
                assertThat(metric.getLongGaugeData().getPoints())
                    .extracting(LongPointData::getValue, PointData::getAttributes)
                    .containsExactlyInAnyOrder(
                        tuple(4L, Attributes.of(metricAttribute, "y")),
                        tuple(1L, Attributes.of(metricAttribute, "x")));
              } else {
                // sum aggregation
                assertThat(metric.getLongSumData().getPoints())
                    .extracting(LongPointData::getValue, PointData::getAttributes)
                    .containsExactlyInAnyOrder(
                        tuple(6L, Attributes.of(metricAttribute, "y")),
                        tuple(4L, Attributes.of(metricAttribute, "x")));
              }
            });
  }

  private Collection<MetricData> testMetric(
      String mbean, List<MetricAttribute> attributes, MetricInfo.Type metricType)
      throws MalformedObjectNameException {
    JmxMetricInsight metricInsight = JmxMetricInsight.createService(sdk, 0);
    MetricConfiguration metricConfiguration = new MetricConfiguration();
    List<MetricExtractor> extractors = new ArrayList<>();

    MetricInfo metricInfo = new MetricInfo("test.metric", "description", null, "1", metricType);
    BeanAttributeExtractor beanExtractor = BeanAttributeExtractor.fromName("Value");
    MetricExtractor extractor = new MetricExtractor(beanExtractor, metricInfo, attributes);
    extractors.add(extractor);
    MetricDef metricDef =
        new MetricDef(BeanGroup.forBeans(Collections.singletonList(mbean)), extractors);

    metricConfiguration.addMetricDef(metricDef);
    metricInsight.startLocal(metricConfiguration);

    return waitMetricsReceived();
  }

  private static void checkSingleValue(
      Collection<MetricData> data, long expectedValue, MetricInfo.Type metricType) {
    assertThat(data)
        .isNotEmpty()
        .satisfiesExactlyInAnyOrder(
            metric -> {
              assertThat(metric.getName()).isEqualTo("test.metric");

              Collection<LongPointData> points;
              if (metricType == MetricInfo.Type.GAUGE) {
                points = metric.getLongGaugeData().getPoints();
              } else {
                points = metric.getLongSumData().getPoints();
              }
              assertThat(points)
                  .extracting(LongPointData::getValue, PointData::getAttributes)
                  .containsExactlyInAnyOrder(tuple(expectedValue, Attributes.empty()));
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
