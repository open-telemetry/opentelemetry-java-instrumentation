/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_2.server;

import com.google.common.base.VerifyException;
import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.Account;
import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.ThriftService;
import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.User;
import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.UserAccount;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.TException;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused", "all"})
public class ThriftServiceImpl implements ThriftService.Iface {

  public ThriftServiceImpl() {}

  @Override
  public String sayHello(String zone, String name) {
    return "Hello " + zone + "s' " + name;
  }

  @Override
  public String withDelay(int delay) {
    try {
      TimeUnit.SECONDS.sleep((long) delay);
    } catch (InterruptedException var3) {
      InterruptedException e = var3;
      throw new VerifyException(e);
    }

    return "delay " + delay;
  }

  @Override
  public String withoutArgs() {
    return "no args";
  }

  @Override
  public String withError() {
    throw new VerifyException("fail");
  }

  @Override
  public String withCollisioin(String input) {
    return input;
  }

  @Override
  public void noReturn(int delay) throws TException {}

  @Override
  public void oneWayHasArgs(int delay) throws TException {}

  @Override
  public void oneWay() {}

  @Override
  public void oneWayWithError() {
    throw new VerifyException("fail");
  }

  @Override
  public UserAccount data(User user, Account account) {
    return new UserAccount(user, account);
  }
}
