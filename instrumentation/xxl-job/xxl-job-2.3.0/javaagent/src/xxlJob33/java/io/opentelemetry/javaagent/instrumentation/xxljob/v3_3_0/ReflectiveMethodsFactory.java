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

  private static final Object SINGLETON_OBJECT = new ReflectObject();

  static Object getTarget() {
    return SINGLETON_OBJECT;
  }

  static Method getMethod() {
    try {
      return SINGLETON_OBJECT.getClass().getMethod("echo", String.class);
    } catch (Throwable t) {
      // Ignore
    }
    return null;
  }

  static Method getInitMethod() {
    try {
      return SINGLETON_OBJECT.getClass().getMethod("initMethod");
    } catch (Throwable t) {
      // Ignore
    }
    return null;
  }

  static Method getDestroyMethod() {
    try {
      return SINGLETON_OBJECT.getClass().getMethod("destroyMethod");
    } catch (Throwable t) {
      // Ignore
    }
    return null;
  }
}
