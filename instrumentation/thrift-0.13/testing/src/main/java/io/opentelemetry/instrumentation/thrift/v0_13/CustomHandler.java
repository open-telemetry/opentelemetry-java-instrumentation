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

public class CustomHandler implements CustomService.Iface {

  @Override
  public String say(String text, String text2) {
    return "Say " + text + " " + text2;
  }

  @Override
  public String withDelay(int seconds) {
    try {
      SECONDS.sleep(seconds);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return "delay " + seconds;
  }

  @Override
  public String withoutArgs() {
    return "no args";
  }

  @Override
  public String withError() {
    throw new IllegalStateException("fail");
  }

  @Override
  public String withCollision(String input) {
    return input;
  }

  @Override
  public void oneWay() {}

  @Override
  public void oneWayWithError() {
    throw new IllegalStateException("fail");
  }

  @Override
  public UserWithAddress save(User user, Address address) {
    return new UserWithAddress(user, address);
  }
}
