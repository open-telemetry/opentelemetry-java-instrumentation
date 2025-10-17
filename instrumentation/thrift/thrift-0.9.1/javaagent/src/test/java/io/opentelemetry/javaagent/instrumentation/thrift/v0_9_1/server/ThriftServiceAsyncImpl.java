/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.ThriftService;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.testcontainers.shaded.com.google.common.base.VerifyException;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused", "all"})
public class ThriftServiceAsyncImpl implements ThriftService.AsyncIface {
  public ThriftServiceAsyncImpl() {}

  @Override
  public void sayHello(String zone, String name, AsyncMethodCallback resultHandler)
      throws TException {
    resultHandler.onComplete("Hello " + zone + "s' " + name);
  }

  @Override
  public void withError(AsyncMethodCallback resultHandler) throws TException {
    throw new VerifyException("fail");
  }

  @Override
  public void noReturn(int delay, AsyncMethodCallback resultHandler) throws TException {
    resultHandler.onComplete(null);
  }

  @Override
  public void oneWay(AsyncMethodCallback resultHandler) throws TException {}

  @Override
  public void oneWayWithError(AsyncMethodCallback resultHandler) throws TException {
    throw new VerifyException("fail");
  }
}
