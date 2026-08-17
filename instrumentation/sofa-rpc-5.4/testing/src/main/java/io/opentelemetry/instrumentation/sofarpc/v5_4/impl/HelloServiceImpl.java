/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4.impl;

import io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService;

public class HelloServiceImpl implements HelloService {
  @Override
  public String hello(String hello) {
    return hello;
  }
}
