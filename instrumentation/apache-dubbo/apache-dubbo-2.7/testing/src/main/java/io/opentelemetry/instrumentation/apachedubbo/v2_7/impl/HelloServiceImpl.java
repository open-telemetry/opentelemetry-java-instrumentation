/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.impl;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;

public class HelloServiceImpl implements HelloService {
  @Override
  public String hello(String hello) {
    return hello;
  }
}
