/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import groovyjarjarantlr4.v4.runtime.misc.Nullable;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import java.util.List;

public class AttributeKeyPair<T> {

  private final AttributeKey<T> key;
  private final T value;

  AttributeKeyPair(AttributeKey<T> key, T value) {
    this.key = key;
    this.value = value;
  }

  public static AttributeKeyPair<String> createStringKeyPair(String keyString, String val) {
    return new AttributeKeyPair<>(AttributeKey.stringKey(keyString), val);
  }

  public static AttributeKeyPair<List<String>> createStringArrayKeyPair(
      String keyString, List<String> val) {
    return new AttributeKeyPair<>(AttributeKey.stringArrayKey(keyString), val);
  }

  public AttributeType getType() {
    return key.getType();
  }

  @SuppressWarnings("unchecked")
  public AttributeKey<String> getStringKey() {
    if (key.getType() != AttributeType.STRING) {
      return null;
    }
    return (AttributeKey<String>) key;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public AttributeKey<List<String>> getStringArrayKey() {
    if (key.getType() != AttributeType.STRING_ARRAY) {
      return null;
    }
    return (AttributeKey<List<String>>) key;
  }

  @SuppressWarnings("unchecked")
  public String getStringVal() {
    if (key.getType() != AttributeType.STRING) {
      return null;
    }
    return (String) value;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public List<String> getStringArrayVal() {
    if (key.getType() != AttributeType.STRING_ARRAY) {
      return null;
    }
    return (List<String>) value;
  }
}
