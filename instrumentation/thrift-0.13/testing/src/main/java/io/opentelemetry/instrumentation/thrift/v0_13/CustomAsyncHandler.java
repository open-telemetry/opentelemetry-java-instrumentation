/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Thrift Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import static java.util.concurrent.TimeUnit.SECONDS;

import custom.Address;
import custom.CustomService;
import custom.User;
import custom.UserWithAddress;
import org.apache.thrift.async.AsyncMethodCallback;

public class CustomAsyncHandler implements CustomService.AsyncIface {

  @Override
  public void say(String text, String text2, AsyncMethodCallback<String> resultHandler) {
    resultHandler.onComplete("Say " + text + " " + text2);
  }

  @Override
  public void withDelay(int seconds, AsyncMethodCallback<String> resultHandler) {
    try {
      SECONDS.sleep(seconds);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    resultHandler.onComplete("delay " + seconds);
  }

  @Override
  public void withoutArgs(AsyncMethodCallback<String> resultHandler) {
    resultHandler.onComplete("no args");
  }

  @Override
  public void withError(AsyncMethodCallback<String> resultHandler) {
    resultHandler.onError(new IllegalStateException("fail"));
  }

  @Override
  public void withCollision(String input, AsyncMethodCallback<String> resultHandler) {
    resultHandler.onComplete(input);
  }

  @Override
  public void oneWay(AsyncMethodCallback<Void> resultHandler) {}

  @Override
  public void oneWayWithError(AsyncMethodCallback<Void> resultHandler) {
    resultHandler.onError(new IllegalStateException("fail"));
  }

  @Override
  public void save(User user, Address address, AsyncMethodCallback<UserWithAddress> resultHandler) {
    resultHandler.onComplete(new UserWithAddress(user, address));
  }
}
