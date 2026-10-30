/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

public interface JedisServerErrorRequest {
  void setServerErrorContext(JedisServerErrorContext context);
}
