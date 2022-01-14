/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springrmi.server;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;

public final class ServerAttributesExtractor extends RpcAttributesExtractor<ClassAndMethod, Void> {

  @Override
  protected String system(ClassAndMethod classAndMethod) {
    return "spring_rmi";
  }

  @Override
  protected String service(ClassAndMethod classAndMethod) {
    return classAndMethod.declaringClass().getName();
  }

  @Override
  protected String method(ClassAndMethod classAndMethod) {
    return classAndMethod.methodName();
  }
}
