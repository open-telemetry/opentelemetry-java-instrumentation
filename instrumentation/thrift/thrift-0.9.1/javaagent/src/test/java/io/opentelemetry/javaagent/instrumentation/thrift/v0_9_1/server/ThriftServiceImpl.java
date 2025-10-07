/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.ThriftService;
import org.apache.thrift.TException;
import org.testcontainers.shaded.com.google.common.base.VerifyException;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused", "all"})
public class ThriftServiceImpl implements ThriftService.Iface {

  public ThriftServiceImpl() {}

  @Override
  public String sayHello(String zone, String name) {
    return "Hello " + zone + "s' " + name;
  }

  @Override
  public String withError() {
    throw new VerifyException("fail");
  }

  @Override
  public void noReturn(int delay) throws TException {}

  @Override
  public void oneWay() {}

  @Override
  public void oneWayWithError() {
    throw new VerifyException("fail");
  }
}
