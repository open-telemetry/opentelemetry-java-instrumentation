/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;

public final class ServerAttributesGetter implements RpcAttributesGetter<ClassAndMethod, Void> {

  @Override
  public String getSystem(ClassAndMethod classAndMethod) {
    return "spring_rmi";
  }

  @Override
  public String getService(ClassAndMethod classAndMethod) {
    return classAndMethod.declaringClass().getName();
  }

  @Deprecated
  @Override
  public String getMethod(ClassAndMethod classAndMethod) {
    return classAndMethod.methodName();
  }
}
