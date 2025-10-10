/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.jmx.engine.MetricAttribute;
import java.util.stream.Stream;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class MetricStructureTest {

  @ParameterizedTest
  @MethodSource("constantAttributes")
  void metricAttribute_constant(String target, String expectedValue) {
    MetricAttribute ma = MetricStructure.buildMetricAttribute("name", target);
    assertThat(ma.getAttributeName()).isEqualTo("name");
    assertThat(ma.isStateAttribute()).isFalse();
    assertThat(ma.acquireAttributeValue(null, null)).isEqualTo(expectedValue);
  }

  // Arguments: target, expectedValue
  static Stream<Arguments> constantAttributes() {
    return Stream.of(
        Arguments.of("const(Hello)", "Hello"), Arguments.of("lowercase(const(Hello))", "hello"));
  }

  @ParameterizedTest
  @MethodSource("beanAttributes")
  void metricAttribute_beanAttribute(String target, String value, String expectedValue)
      throws Exception {
    MetricAttribute ma = MetricStructure.buildMetricAttribute("name", target);
    assertThat(ma.getAttributeName()).isEqualTo("name");
    assertThat(ma.isStateAttribute()).isFalse();

    ObjectName objectName = new ObjectName("test:name=_beanAttribute");
    MBeanServerConnection mockConnection = mock(MBeanServerConnection.class);

    MBeanInfo mockBeanInfo = mock(MBeanInfo.class);
    when(mockBeanInfo.getAttributes())
        .thenReturn(
            new MBeanAttributeInfo[] {
              new MBeanAttributeInfo("beanAttribute", "java.lang.String", "", true, false, false)
            });
    when(mockConnection.getMBeanInfo(objectName)).thenReturn(mockBeanInfo);
    when(mockConnection.getAttribute(objectName, "beanAttribute")).thenReturn(value);

    assertThat(ma.acquireAttributeValue(mockConnection, objectName)).isEqualTo(expectedValue);
  }

  // Arguments: target, value, expectedValue
  static Stream<Arguments> beanAttributes() {
    return Stream.of(
        Arguments.of("beanattr(beanAttribute)", "Hello", "Hello"),
        Arguments.of("lowercase(beanattr(beanAttribute))", "Hello", "hello"));
  }

  @ParameterizedTest
  @MethodSource("beanParams")
  void metricAttribute_beanParam(String target, String value, String expectedValue)
      throws Exception {
    MetricAttribute ma = MetricStructure.buildMetricAttribute("name", target);
    assertThat(ma.getAttributeName()).isEqualTo("name");
    assertThat(ma.isStateAttribute()).isFalse();

    ObjectName objectName = new ObjectName("test:name=" + value);
    MBeanServerConnection mockConnection = mock(MBeanServerConnection.class);

    assertThat(ma.acquireAttributeValue(mockConnection, objectName)).isEqualTo(expectedValue);
  }

  // Arguments: target, value, expectedValue
  static Stream<Arguments> beanParams() {
    return Stream.of(
        Arguments.of("param(name)", "Hello", "Hello"),
        Arguments.of("lowercase(param(name))", "Hello", "hello"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing(name)", // non-existing target
        "param()", // missing parameter
        "param( )", // missing parameter with empty string
        "param(name)a", // something after parenthesis
        "lowercase()", // misng target in modifier
        "lowercase(param(name)", // missing parenthesis for modifier
        "lowercase(missing(name))", // non-existing target within modifier
        "lowercase(param())", // missing parameter in modifier
        "lowercase(param( ))", // missing parameter in modifier with empty string
        "lowercase(param))", // missing parenthesis within modifier
      })
  void invalidTargetSyntax(String target) {
    assertThatThrownBy(() -> MetricStructure.buildMetricAttribute("metric_attribute", target))
        .isInstanceOf(IllegalArgumentException.class)
        .describedAs(
            "exception should be thrown with original expression to help end-user understand the syntax error")
        .hasMessageContaining(target);
  }
}
