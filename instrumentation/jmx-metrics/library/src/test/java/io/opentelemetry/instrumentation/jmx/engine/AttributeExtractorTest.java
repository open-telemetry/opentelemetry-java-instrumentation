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
import org.junit.jupiter.api.Test;

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
  }

  private static class Test1 implements Test1MBean {
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
      return 12;
    }

    @Override
    public long getLongAttribute() {
      return 13;
    }

    @Override
    public float getFloatAttribute() {
      return 14.0f;
    }

    @Override
    public double getDoubleAttribute() {
      return 15.0;
    }

    @Override
    public String getStringAttribute() {
      return "";
    }
  }

  private static final String DOMAIN = "otel.jmx.test";
  private static final String OBJECT_NAME = "otel.jmx.test:type=Test1";
  private static ObjectName objectName;
  private static MBeanServer theServer;

  @BeforeAll
  static void setUp() throws Exception {
    theServer = MBeanServerFactory.createMBeanServer(DOMAIN);
    Test1 test1 = new Test1();
    objectName = new ObjectName(OBJECT_NAME);
    theServer.registerMBean(test1, objectName);
  }

  @AfterAll
  static void tearDown() {
    MBeanServerFactory.releaseMBeanServer(theServer);
    theServer = null;
  }

  @Test
  void testSetup() throws Exception {
    Set<ObjectName> set = theServer.queryNames(objectName, null);
    assertThat(set == null).isFalse();
    assertThat(set.size() == 1).isTrue();
    assertThat(set.contains(objectName)).isTrue();
  }

  @Test
  void testByteAttribute() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("ByteAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info == null).isFalse();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @Test
  void testByteAttributeValue() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("ByteAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number == null).isFalse();
    assertThat(number.longValue() == 10).isTrue();
  }

  @Test
  void testShortAttribute() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("ShortAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info == null).isFalse();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @Test
  void testShortAttributeValue() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("ShortAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number == null).isFalse();
    assertThat(number.longValue() == 11).isTrue();
  }

  @Test
  void testIntAttribute() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("IntAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info == null).isFalse();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @Test
  void testIntAttributeValue() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("IntAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number == null).isFalse();
    assertThat(number.longValue() == 12).isTrue();
  }

  @Test
  void testLongAttribute() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("LongAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info == null).isFalse();
    assertThat(info.usesDoubleValues()).isFalse();
  }

  @Test
  void testLongAttributeValue() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("LongAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number == null).isFalse();
    assertThat(number.longValue() == 13).isTrue();
  }

  @Test
  void testFloatAttribute() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("FloatAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info == null).isFalse();
    assertThat(info.usesDoubleValues()).isTrue();
  }

  @Test
  void testFloatAttributeValue() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("FloatAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number == null).isFalse();
    assertThat(number.doubleValue() == 14.0).isTrue(); // accurate representation
  }

  @Test
  void testDoubleAttribute() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("DoubleAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info == null).isFalse();
    assertThat(info.usesDoubleValues()).isTrue();
  }

  @Test
  void testDoubleAttributeValue() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("DoubleAttribute");
    Number number = extractor.extractNumericalAttribute(theServer, objectName);
    assertThat(number == null).isFalse();
    assertThat(number.doubleValue() == 15.0).isTrue(); // accurate representation
  }

  @Test
  void testStringAttribute() throws Exception {
    BeanAttributeExtractor extractor = BeanAttributeExtractor.fromName("StringAttribute");
    AttributeInfo info = extractor.getAttributeInfo(theServer, objectName);
    assertThat(info == null).isTrue();
  }
}
