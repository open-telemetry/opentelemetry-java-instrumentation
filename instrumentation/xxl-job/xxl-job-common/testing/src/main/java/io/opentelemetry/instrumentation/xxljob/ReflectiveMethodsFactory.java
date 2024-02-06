/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.xxljob;

import com.xxl.job.core.biz.model.ReturnT;
import java.lang.reflect.Method;

public final class ReflectiveMethodsFactory {

  private ReflectiveMethodsFactory() {}

  public static class ReflectObject {

    private String s;

    private ReflectObject() {}

    public void initMethod() {
      s = "init";
    }

    public void destroyMethod() {
      s = null;
    }

    public String echo() {
      return "echo: " + s;
    }

    public ReturnT<String> echo(String param) {
      return new ReturnT<>("echo: " + param);
    }
  }

  private static final Object SINGLE_OBJECT = new ReflectObject();

  static Object getTarget() {
    return SINGLE_OBJECT;
  }

  static Method getMethod() {
    try {
      return SINGLE_OBJECT.getClass().getMethod("echo");
    } catch (Throwable t) {
      // Ignore
    }
    return null;
  }

  static Method getMethod1() {
    try {
      return SINGLE_OBJECT.getClass().getMethod("echo", String.class);
    } catch (Throwable t) {
      // Ignore
    }
    return null;
  }

  static Method getInitMethod() {
    try {
      return SINGLE_OBJECT.getClass().getMethod("initMethod");
    } catch (Throwable t) {
      // Ignore
    }
    return null;
  }

  static Method getDestroyMethod() {
    try {
      return SINGLE_OBJECT.getClass().getMethod("destroyMethod");
    } catch (Throwable t) {
      // Ignore
    }
    return null;
  }
}
