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
    try {
      return ReflectObject.class.getMethod("echo", String.class);
    } catch (Throwable t) {
      return null;
    }
  }

  static Method getInitMethod() {
    try {
      return ReflectObject.class.getMethod("initMethod");
    } catch (Throwable t) {
      return null;
    }
  }

  static Method getDestroyMethod() {
    try {
      return ReflectObject.class.getMethod("destroyMethod");
    } catch (Throwable t) {
      return null;
    }
  }
}
