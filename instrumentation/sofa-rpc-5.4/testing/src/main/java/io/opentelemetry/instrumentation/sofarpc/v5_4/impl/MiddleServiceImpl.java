/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4.impl;

import com.alipay.sofa.rpc.config.ConsumerConfig;
import io.opentelemetry.instrumentation.sofarpc.v5_4.api.HelloService;
import io.opentelemetry.instrumentation.sofarpc.v5_4.api.MiddleService;

public class MiddleServiceImpl implements MiddleService {

  private final ConsumerConfig<HelloService> consumerConfig;

  public MiddleServiceImpl(ConsumerConfig<HelloService> consumerConfig) {
    this.consumerConfig = consumerConfig;
  }

  @Override
  public String hello(String hello) {
    HelloService helloService = consumerConfig.refer();
    return helloService.hello(hello);
  }
}
