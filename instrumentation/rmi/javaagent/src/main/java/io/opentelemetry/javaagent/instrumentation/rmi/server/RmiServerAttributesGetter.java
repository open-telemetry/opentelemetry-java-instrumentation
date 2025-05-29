/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;

enum RmiServerAttributesGetter implements RpcAttributesGetter<ClassAndMethod> {
  INSTANCE;

  @Override
  public String getSystem(ClassAndMethod classAndMethod) {
    return "java_rmi";
  }

  @Override
  public String getService(ClassAndMethod classAndMethod) {
    return classAndMethod.declaringClass().getName();
  }

  @Override
  public String getMethod(ClassAndMethod classAndMethod) {
    return classAndMethod.methodName();
  }
}
