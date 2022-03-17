/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springrmi.server;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;

public enum ServerAttributesGetter implements RpcAttributesGetter<ClassAndMethod> {
  INSTANCE;

  @Override
  public String system(ClassAndMethod classAndMethod) {
    return "spring_rmi";
  }

  @Override
  public String service(ClassAndMethod classAndMethod) {
    return classAndMethod.declaringClass().getName();
  }

  @Override
  public String method(ClassAndMethod classAndMethod) {
    return classAndMethod.methodName();
  }
}
