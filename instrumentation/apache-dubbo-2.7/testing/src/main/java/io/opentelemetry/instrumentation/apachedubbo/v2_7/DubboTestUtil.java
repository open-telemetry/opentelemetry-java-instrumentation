/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;

/** compatible with dubbo3.x and dubbo 2.7 */
class DubboTestUtil {

  private DubboTestUtil() {}

  static Object newFrameworkModel() {
    try {
      // only present in latest dep
      return Class.forName("org.apache.dubbo.rpc.model.FrameworkModel")
          .getDeclaredConstructor()
          .newInstance();
    } catch (ReflectiveOperationException exception) {
      return null;
    }
  }

  @SuppressWarnings("ThrowSpecificExceptions")
  static DubboBootstrap newDubboBootstrap() {
    Object newFrameworkModel = newFrameworkModel();
    try {
      if (newFrameworkModel == null) {
        return newDubboBootstrapV27();
      } else {
        return newDubboBootstrapV3(newFrameworkModel);
      }
    } catch (ReflectiveOperationException exception) {
      throw new RuntimeException("Error creating new DubboBootstrap instance", exception);
    }
  }

  private static DubboBootstrap newDubboBootstrapV3(Object newFrameworkModel)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Method getInstance =
        DubboBootstrap.class.getDeclaredMethod("newInstance", newFrameworkModel.getClass());
    return (DubboBootstrap) getInstance.invoke(null, newFrameworkModel);
  }

  private static DubboBootstrap newDubboBootstrapV27()
      throws NoSuchMethodException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException {
    Constructor<DubboBootstrap> constructor = DubboBootstrap.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
