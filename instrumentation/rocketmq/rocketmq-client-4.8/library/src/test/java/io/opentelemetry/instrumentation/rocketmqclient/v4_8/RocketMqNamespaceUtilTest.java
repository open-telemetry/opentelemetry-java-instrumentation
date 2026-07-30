/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RocketMqNamespaceUtilTest {

  @Test
  void readsNamespaceFromSupportedContext() {
    ContextWithNamespace context = new ContextWithNamespace();

    assertThat(RocketMqNamespaceUtil.getNamespace(context)).isEqualTo("namespace");
    assertThat(RocketMqNamespaceUtil.getNamespace(context)).isEqualTo("namespace");
  }

  @Test
  void handlesContextWithoutNamespace() {
    Object context = new Object();

    assertThat(RocketMqNamespaceUtil.getNamespace(context)).isNull();
    assertThat(RocketMqNamespaceUtil.getNamespace(context)).isNull();
  }

  public static class ContextWithNamespace {
    public String getNamespace() {
      return "namespace";
    }
  }
}
