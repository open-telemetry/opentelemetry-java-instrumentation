/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import com.google.auto.value.AutoValue;
import com.netflix.hystrix.HystrixInvokableInfo;

@AutoValue
public abstract class HystrixRequest {

  public static HystrixRequest create(HystrixInvokableInfo<?> command, String methodName) {
    return new AutoValue_HystrixRequest(command, methodName);
  }

  public abstract HystrixInvokableInfo<?> command();

  public abstract String methodName();

  String spanName() {
    String commandName = command().getCommandKey().name();
    String groupName = command().getCommandGroup().name();
    return groupName + "." + commandName + "." + methodName();
  }
}
