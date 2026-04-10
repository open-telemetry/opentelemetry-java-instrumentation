/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.xxljob;

import com.xxl.job.core.biz.model.ReturnT;
import java.lang.reflect.Method;

class ReflectiveMethodsFactory {

  private ReflectiveMethodsFactory() {}

  public static class ReflectObject {

    private ReflectObject() {}

    public void initMethod() {}

    public void destroyMethod() {}

    public ReturnT<String> echo(String param) {
      ReturnT<String> result = new ReturnT<>();
      result.setContent("echo: " + param);
      return result;
    }
  }

  private static final Object SINGLETON_OBJECT = new ReflectObject();

  static Object getTarget() {
    return SINGLETON_OBJECT;
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
      return SINGLETON_OBJECT.getClass().getMethod(name, parameterTypes);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to resolve reflective method: " + name, e);
    }
  }
}
