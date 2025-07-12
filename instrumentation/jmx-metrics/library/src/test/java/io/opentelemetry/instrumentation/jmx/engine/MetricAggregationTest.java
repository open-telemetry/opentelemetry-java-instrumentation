/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.junit.jupiter.params.ParameterizedInvocationConstants.ARGUMENTS_PLACEHOLDER;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
import org.junit.jupiter.api.extension.RegisterExtension;
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

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  // used to generate non-conflicting metric names at runtime
  private static final AtomicInteger metricCounter = new AtomicInteger(0);

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

  @AfterEach
  void after() throws Exception {
    ObjectName objectName = new ObjectName(DOMAIN + ":type=" + Hello.class.getSimpleName() + ",*");
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
  }

  private static ObjectName getObjectName(@Nullable String a, @Nullable String b)
      throws MalformedObjectNameException {
    StringBuilder parts = new StringBuilder();
    parts.append("otel.jmx.test:type=").append(Hello.class.getSimpleName());
    if (a != null) {
      parts.append(",a=").append(a);
    }
    if (b != null) {
      parts.append(",b=").append(b);
    }
    return new ObjectName(parts.toString());
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

    String metricName = generateMetricName(metricType);
    startTestMetric(metricName, bean.toString(), Collections.emptyList(), metricType);
    waitAndAssertMetric(
        metricName, metricType, point -> point.hasValue(42).hasAttributes(Attributes.empty()));
  }

  @ParameterizedTest(name = ARGUMENTS_PLACEHOLDER)
  @MethodSource("metricTypes")
  void aggregateOneParam(MetricInfo.Type metricType) throws Exception {
    theServer.registerMBean(new Hello(42), getObjectName("value1", null));
    theServer.registerMBean(new Hello(37), getObjectName("value2", null));

    String bean = getObjectName("*", null).toString();
    String metricName = generateMetricName(metricType);
    startTestMetric(metricName, bean, Collections.emptyList(), metricType);

    // last-value aggregation produces an unpredictable result unless a single mbean instance is
    // used
    // test here is only used as a way to document behavior and should not be considered a feature
    long expected = metricType == MetricInfo.Type.GAUGE ? 37 : 79;
    waitAndAssertMetric(
        metricName,
        metricType,
        point -> point.hasValue(expected).hasAttributes(Attributes.empty()));
  }

  @ParameterizedTest(name = ARGUMENTS_PLACEHOLDER)
  @MethodSource("metricTypes")
  void aggregateMultipleParams(MetricInfo.Type metricType) throws Exception {
    theServer.registerMBean(new Hello(1), getObjectName("1", "x"));
    theServer.registerMBean(new Hello(2), getObjectName("2", "y"));
    theServer.registerMBean(new Hello(3), getObjectName("3", "x"));
    theServer.registerMBean(new Hello(4), getObjectName("4", "y"));

    String bean = getObjectName("*", "*").toString();
    String metricName = generateMetricName(metricType);
    startTestMetric(metricName, bean, Collections.emptyList(), metricType);

    // last-value aggregation produces an unpredictable result unless a single mbean instance is
    // used
    // test here is only used as a way to document behavior and should not be considered a feature
    long expected = metricType == MetricInfo.Type.GAUGE ? 1 : 10;
    waitAndAssertMetric(
        metricName,
        metricType,
        point -> point.hasValue(expected).hasAttributes(Attributes.empty()));
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
    String metricName = generateMetricName(metricType);
    startTestMetric(metricName, bean, attributes, metricType);

    AttributeKey<String> metricAttribute = AttributeKey.stringKey("test.metric.param");
    if (metricType == MetricInfo.Type.GAUGE) {
      waitAndAssertMetric(
          metricName,
          metricType,
          point -> point.hasValue(1).hasAttribute(metricAttribute, "x"),
          point -> point.hasValue(4).hasAttribute(metricAttribute, "y"));
    } else {
      waitAndAssertMetric(
          metricName,
          metricType,
          point -> point.hasValue(4).hasAttribute(metricAttribute, "x"),
          point -> point.hasValue(6).hasAttribute(metricAttribute, "y"));
    }
  }

  private static String generateMetricName(MetricInfo.Type metricType) {
    // generate a sequential metric name that prevents naming conflicts and unexpected behaviors
    return "test.metric"
        + metricCounter.incrementAndGet()
        + "."
        + metricType.name().toLowerCase(Locale.ROOT);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  private static void waitAndAssertMetric(
      String metricName, MetricInfo.Type metricType, Consumer<LongPointAssert>... pointAsserts) {

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metricName,
        metrics ->
            metrics.anySatisfy(
                metricData -> {
                  MetricAssert metricAssert =
                      assertThat(metricData).hasDescription("description").hasUnit("1");
                  if (metricType == MetricInfo.Type.GAUGE) {
                    metricAssert.hasLongGaugeSatisfying(
                        gauge -> gauge.hasPointsSatisfying(pointAsserts));
                  } else {
                    metricAssert.hasLongSumSatisfying(sum -> sum.hasPointsSatisfying(pointAsserts));
                  }
                }));
  }

  private static void startTestMetric(
      String metricName, String mbean, List<MetricAttribute> attributes, MetricInfo.Type metricType)
      throws MalformedObjectNameException {
    JmxMetricInsight metricInsight = JmxMetricInsight.createService(testing.getOpenTelemetry(), 0);
    MetricConfiguration metricConfiguration = new MetricConfiguration();
    List<MetricExtractor> extractors = new ArrayList<>();

    MetricInfo metricInfo = new MetricInfo(metricName, "description", null, "1", metricType);
    BeanAttributeExtractor beanExtractor = BeanAttributeExtractor.fromName("Value");
    MetricExtractor extractor = new MetricExtractor(beanExtractor, metricInfo, attributes);
    extractors.add(extractor);
    MetricDef metricDef =
        new MetricDef(BeanGroup.forBeans(Collections.singletonList(mbean)), extractors);

    metricConfiguration.addMetricDef(metricDef);
    metricInsight.startLocal(metricConfiguration);
  }
}
