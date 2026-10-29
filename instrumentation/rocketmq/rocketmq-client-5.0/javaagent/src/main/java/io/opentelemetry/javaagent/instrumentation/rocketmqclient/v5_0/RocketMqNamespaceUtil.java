/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import javax.annotation.Nullable;

final class RocketMqNamespaceUtil {

  @Nullable
  static String normalize(@Nullable String namespace) {
    return namespace == null || namespace.isEmpty() ? null : namespace;
  }

  private RocketMqNamespaceUtil() {}
}
