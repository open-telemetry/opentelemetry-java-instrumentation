/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_1;

import io.opentelemetry.test.WebServiceDefinitionInterface;
import io.opentelemetry.test.WebServiceFromInterface;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ProxyInvocationHandler implements InvocationHandler {

  WebServiceDefinitionInterface target;

  public ProxyInvocationHandler(WebServiceFromInterface webServiceFromInterface) {
    target = webServiceFromInterface;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return method.invoke(target, args);
  }
}
