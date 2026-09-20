/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import jakarta.jms.CompletionListener;
import jakarta.jms.Destination;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * A {@link JMSProducer} that never delegates to a {@code MessageProducer}. The only advice that can
 * run on it is the one on {@code JMSProducer.send(Destination, Message)}, so a span can only come
 * from the simplified-API instrumentation.
 */
class StubJmsProducer implements JMSProducer {

  @Override
  public JMSProducer send(Destination destination, Message message) {
    return this;
  }

  @Override
  public JMSProducer send(Destination destination, String body) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer send(Destination destination, Map<String, Object> body) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer send(Destination destination, byte[] body) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer send(Destination destination, Serializable body) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setDisableMessageID(boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getDisableMessageID() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setDisableMessageTimestamp(boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getDisableMessageTimestamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setDeliveryMode(int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getDeliveryMode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setPriority(int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getPriority() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setTimeToLive(long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimeToLive() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setDeliveryDelay(long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getDeliveryDelay() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setAsync(CompletionListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletionListener getAsync() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, byte value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, short value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, float value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, double value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setProperty(String name, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer clearProperties() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean propertyExists(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getBooleanProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte getByteProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public short getShortProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getIntProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLongProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public float getFloatProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getDoubleProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getStringProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getObjectProperty(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> getPropertyNames() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setJMSCorrelationIDAsBytes(byte[] value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] getJMSCorrelationIDAsBytes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setJMSCorrelationID(String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getJMSCorrelationID() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setJMSType(String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getJMSType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JMSProducer setJMSReplyTo(Destination destination) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Destination getJMSReplyTo() {
    throw new UnsupportedOperationException();
  }
}
