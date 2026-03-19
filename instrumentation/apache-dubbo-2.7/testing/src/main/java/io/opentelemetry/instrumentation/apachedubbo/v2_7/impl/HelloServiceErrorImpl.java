/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.impl;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;

public class HelloServiceErrorImpl implements HelloService {
  @Override
  @SuppressWarnings("ThrowSpecificExceptions") // intentionally throws RuntimeException for testing
  public String hello(String hello) {
    throw new RuntimeException("error: " + hello);
  }
}
