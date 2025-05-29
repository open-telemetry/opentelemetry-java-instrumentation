/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

  @Test
  void testByteAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("ByteAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNotNull();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @Test
  void testByteAttributeValue() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("ByteAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number).isNotNull();
    assertThat(number.longValue()).isEqualTo(10);
  }

  @Test
  void testShortAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("ShortAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNotNull();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @Test
  void testShortAttributeValue() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("ShortAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number).isNotNull();
    assertThat(number.longValue()).isEqualTo(11);
  }

  @Test
  void testIntAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("IntAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNotNull();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @Test
  void testIntAttributeValue() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("IntAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number).isNotNull();
    assertThat(number.longValue()).isEqualTo(12);
  }

  @Test
  void testLongAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("LongAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNotNull();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @Test
  void testLongAttributeValue() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("LongAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number).isNotNull();
    assertThat(number.longValue()).isEqualTo(13);
  }

  @Test
  void testFloatAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("FloatAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNotNull();
    assertThat(info.usesDoubleValues()).isTrue();
  }

  @Test
  void testFloatAttributeValue() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("FloatAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number).isNotNull();
    assertThat(number.doubleValue()).isEqualTo(14.0); // accurate representation
  }

  @Test
  void testDoubleAttribute() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("DoubleAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info).isNotNull();
    assertThat(info.usesDoubleValues()).isTrue();
  }

  @Test
  void testDoubleAttributeValue() {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("DoubleAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number).isNotNull();
    assertThat(number.doubleValue()).isEqualTo(15.0); // accurate representation
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
        .describedAs("when value is not negative original numerical value is returned")
        .isEqualTo(filteringExtractor.extractNumericalAttribute(theServer, objectName));

    test1.negativeValues = true;
    Number rawValue = rawExtractor.extractNumericalAttribute(theServer, objectName);
    assertThat(rawValue).isNotNull();
    assertThat(rawValue.doubleValue()).isNegative();
    assertThat(filteringExtractor.extractNumericalAttribute(theServer, objectName))
        .describedAs("negative value should be filtered")
        .isNull();
  }
}
