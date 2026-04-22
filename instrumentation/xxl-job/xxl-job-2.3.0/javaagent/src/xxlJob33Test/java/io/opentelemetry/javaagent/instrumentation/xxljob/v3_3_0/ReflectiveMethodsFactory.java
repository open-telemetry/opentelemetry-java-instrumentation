/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v3_3_0;

import com.xxl.tool.response.Response;
import java.lang.reflect.Method;

class ReflectiveMethodsFactory {

  private ReflectiveMethodsFactory() {}

  public static class ReflectObject {

    private ReflectObject() {}

    public void initMethod() {}

    public void destroyMethod() {}

    public Response<String> echo(String param) {
      Response<String> result = new Response<>();
      result.setData("echo: " + param);
      return result;
    }
  }

  private static final Object singletonObject = new ReflectObject();

  static Object getTarget() {
    return singletonObject;
  }

  static Method getMethod() {
    return getRequiredMethod("echo", String.class);
  }

  static Method getInitMethod() {
    return getRequiredMethod("initMethod");
  }

  static Method getDestroyMethod() {
    return getRequiredMethod("destroyMethod");
  }

  private static Method getRequiredMethod(String name, Class<?>... parameterTypes) {
    try {
      return singletonObject.getClass().getMethod(name, parameterTypes);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to resolve reflective method: " + name, e);
    }
  }
}
