/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Set;
import java.util.stream.Stream;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AttributeExtractorTest {

  // An MBean used for this test
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public interface Test1MBean {

    byte getByteAttribute();

    short getShortAttribute();

    int getIntAttribute();

    long getLongAttribute();

    float getFloatAttribute();

    double getDoubleAttribute();

    String getStringAttribute();

    boolean getBooleanAttribute();

    Enum<?> getEnumAttribute();
  }

  private static class Test1 implements Test1MBean {

    boolean negativeValues;

    @Override
    public byte getByteAttribute() {
      return 10;
    }

    @Override
    public short getShortAttribute() {
      return 11;
    }

    @Override
    public int getIntAttribute() {
      return negativeValues ? -12 : 12;
    }

    @Override
    public long getLongAttribute() {
      return negativeValues ? -13 : 13;
    }

    @Override
    public float getFloatAttribute() {
      return negativeValues ? -14.0f : 14.0f;
    }

    @Override
    public double getDoubleAttribute() {
      return negativeValues ? -15.0 : 15.0;
    }

    @Override
    public String getStringAttribute() {
      return "";
    }

    @Override
    public boolean getBooleanAttribute() {
      return true;
    }

    @Override
    public Enum<?> getEnumAttribute() {
      return DummyEnum.ENUM_VALUE;
    }

    private enum DummyEnum {
      ENUM_VALUE
    }
  }

  private static final String DOMAIN = "otel.jmx.test";
  private static final String OBJECT_NAME = "otel.jmx.test:type=Test1";
  private static final Test1 test1 = new Test1();
  private static ObjectName objectName;
  private static MBeanServer theServer;

  @BeforeAll
  static void setUp() throws Exception {
    theServer = MBeanServerFactory.createMBeanServer(DOMAIN);
    objectName = new ObjectName(OBJECT_NAME);
    theServer.registerMBean(test1, objectName);
  }

  @AfterAll
  static void tearDown() {
    MBeanServerFactory.releaseMBeanServer(theServer);
    theServer = null;
  }

  @BeforeEach
  void reset() {
    test1.negativeValues = false;
  }

  @Test
  void testSetup() {
    Set<ObjectName> set = theServer.queryNames(objectName, null);
    assertThat(set).isNotNull().hasSize(1).contains(objectName);
  }

  private static Stream<Arguments> longAttributes() {
    return Stream.of(
        arguments("ByteAttribute", 10L),
        arguments("ShortAttribute", 11L),
        arguments("IntAttribute", 12L),
        arguments("LongAttribute", 13L));
  }

  private static Stream<String> longAttributeNames() {
    return Stream.of("ByteAttribute", "ShortAttribute", "IntAttribute", "LongAttribute");
  }

  private static Stream<Arguments> doubleAttributes() {
    // accurate representation
    return Stream.of(arguments("FloatAttribute", 14.0), arguments("DoubleAttribute", 15.0));
  }

  private static Stream<String> doubleAttributeNames() {
    return Stream.of("FloatAttribute", "DoubleAttribute");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("longAttributeNames")
  void longAttributeInfo(String attributeName) {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName(attributeName);
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNotNull();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("longAttributes")
  void longAttributeValue(String attributeName, long expectedValue) {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName(attributeName);
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number).isNotNull();
    assertThat(number.longValue()).isEqualTo(expectedValue);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("doubleAttributeNames")
  void doubleAttributeInfo(String attributeName) {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName(attributeName);
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNotNull();
    assertThat(info.usesDoubleValues()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("doubleAttributes")
  void doubleAttributeValue(String attributeName, double expectedValue) {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName(attributeName);
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number).isNotNull();
    assertThat(number.doubleValue()).isEqualTo(expectedValue);
  }

  @Test
  void testStringAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("StringAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNull();
  }

  @Test
  void testBooleanAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("BooleanAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNull();
    assertThat(extractor.extractValue(theServer, objectName)).isEqualTo("true");
  }

  @Test
  void testEnumAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("EnumAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNull();
    assertThat(extractor.extractValue(theServer, objectName)).isEqualTo("ENUM_VALUE");
  }

  @ParameterizedTest
  @ValueSource(strings = {"LongAttribute", "IntAttribute", "DoubleAttribute", "FloatAttribute"})
  void testNegativeFilter(String attributeName) {
    test1.negativeValues = false;
    BeanAttributeExtractor rawExtractor = BeanAttributeExtractor.fromName(attributeName);
    BeanAttributeExtractor filteringExtractor =
        BeanAttributeExtractor.filterNegativeValues(rawExtractor);
    assertThat(rawExtractor.extractNumericalAttribute(theServer, objectName))
        .isNotNull()
        .isEqualTo(filteringExtractor.extractNumericalAttribute(theServer, objectName));

    test1.negativeValues = true;
    Number rawValue = rawExtractor.extractNumericalAttribute(theServer, objectName);
    assertThat(rawValue).isNotNull();
    assertThat(rawValue.doubleValue()).isNegative();
    assertThat(filteringExtractor.extractNumericalAttribute(theServer, objectName)).isNull();
  }
}
