/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_0.server;

import com.google.common.base.VerifyException;
import io.opentelemetry.instrumentation.thrift.v0_9_0.thrift.Account;
import io.opentelemetry.instrumentation.thrift.v0_9_0.thrift.ThriftService;
import io.opentelemetry.instrumentation.thrift.v0_9_0.thrift.User;
import io.opentelemetry.instrumentation.thrift.v0_9_0.thrift.UserAccount;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused", "all"})
public class ThriftServiceAsyncImpl implements ThriftService.AsyncIface {
  public ThriftServiceAsyncImpl() {}

  @Override
  public void sayHello(String zone, String name, AsyncMethodCallback resultHandler)
      throws TException {
    resultHandler.onComplete("Hello " + zone + "s' " + name);
  }

  @Override
  public void withDelay(int delay, AsyncMethodCallback resultHandler) throws TException {
    try {
      TimeUnit.SECONDS.sleep(delay);
    } catch (InterruptedException var4) {
      InterruptedException e = var4;
      throw new VerifyException(e);
    }

    resultHandler.onComplete("delay " + delay);
  }

  @Override
  public void withoutArgs(AsyncMethodCallback resultHandler) throws TException {
    resultHandler.onComplete("no args");
  }

  @Override
  public void withError(AsyncMethodCallback resultHandler) throws TException {
    throw new VerifyException("fail");
  }

  @Override
  public void withCollisioin(String input, AsyncMethodCallback resultHandler) throws TException {
    resultHandler.onComplete(input);
  }

  @Override
  public void oneWayHasArgs(int delay, AsyncMethodCallback resultHandler) throws TException {}

  @Override
  public void oneWay(AsyncMethodCallback resultHandler) throws TException {}

  @Override
  public void oneWayWithError(AsyncMethodCallback resultHandler) throws TException {
    throw new VerifyException("fail");
  }

  @Override
  public void data(User user, Account account, AsyncMethodCallback resultHandler)
      throws TException {
    resultHandler.onComplete(new UserAccount(user, account));
  }

  @Override
  public void noReturn(
      int delay, AsyncMethodCallback<ThriftService.AsyncClient.noReturn_call> resultHandler)
      throws TException {
    resultHandler.onComplete(null);
  }
}
