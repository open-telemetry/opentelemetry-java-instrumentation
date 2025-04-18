/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_14_0.server;

import com.google.common.base.VerifyException;
import io.opentelemetry.instrumentation.thrift.v0_14_0.thrift.Account;
import io.opentelemetry.instrumentation.thrift.v0_14_0.thrift.ThriftService;
import io.opentelemetry.instrumentation.thrift.v0_14_0.thrift.User;
import io.opentelemetry.instrumentation.thrift.v0_14_0.thrift.UserAccount;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

public class ThriftServiceAsyncImpl implements ThriftService.AsyncIface {
  public ThriftServiceAsyncImpl() {}

  @Override
  public void sayHello(String zone, String name, AsyncMethodCallback<String> resultHandler) {
    resultHandler.onComplete("Hello " + zone + "s' " + name);
  }

  @Override
  public void withDelay(int delay, AsyncMethodCallback<String> resultHandler) {
    try {
      TimeUnit.SECONDS.sleep(delay);
    } catch (InterruptedException var4) {
      InterruptedException e = var4;
      throw new VerifyException(e);
    }

    resultHandler.onComplete("delay " + delay);
  }

  @Override
  public void withoutArgs(AsyncMethodCallback<String> resultHandler) {
    resultHandler.onComplete("no args");
  }

  @Override
  public void withError(AsyncMethodCallback<String> resultHandler) {
    throw new VerifyException("fail");
  }

  @Override
  public void withCollisioin(String input, AsyncMethodCallback<String> resultHandler) {
    resultHandler.onComplete(input);
  }

  @Override
  public void noReturn(int delay, AsyncMethodCallback<Void> resultHandler) throws TException {
    resultHandler.onComplete(null);
  }

  @Override
  public void oneWayHasArgs(int delay, AsyncMethodCallback<Void> resultHandler) throws TException {}

  @Override
  public void oneWay(AsyncMethodCallback<Void> resultHandler) {}

  @Override
  public void oneWayWithError(AsyncMethodCallback<Void> resultHandler) {
    throw new VerifyException("fail");
  }

  @Override
  public void data(User user, Account account, AsyncMethodCallback<UserAccount> resultHandler) {
    resultHandler.onComplete(new UserAccount(user, account));
  }
}
