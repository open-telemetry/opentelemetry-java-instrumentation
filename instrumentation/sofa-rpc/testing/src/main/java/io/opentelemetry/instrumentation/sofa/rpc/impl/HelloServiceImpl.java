/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc.impl;

import io.opentelemetry.instrumentation.sofa.rpc.api.HelloService;

public class HelloServiceImpl implements HelloService {
  @Override
  public String hello(String hello) {
    return hello;
  }
}

