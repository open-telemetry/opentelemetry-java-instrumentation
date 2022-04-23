/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.info;

import io.opentelemetry.instrumentation.api.field.VirtualField;

public class ClientEnhanceInfo {
  private final String topic;
  private final String url;

  public ClientEnhanceInfo(String topic, String url) {
    this.topic = topic;
    this.url = url;
  }

  public String getTopic() {
    return topic;
  }

  public String getUrl() {
    return url;
  }


  public static <T> void virtualField(Class<T> klass, T instance, ClientEnhanceInfo info) {
    VirtualField.find(klass, ClientEnhanceInfo.class).set(instance, info);
  }

  public static <T> ClientEnhanceInfo virtualField(Class<T> klass, T instance) {
    return VirtualField.find(klass, ClientEnhanceInfo.class).get(instance);
  }
}
