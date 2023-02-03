/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7

import org.apache.dubbo.config.bootstrap.DubboBootstrap

class DubboTestUtil {
  static newFrameworkModel() {
    try {
      // only present in latest dep
      return Class.forName("org.apache.dubbo.rpc.model.FrameworkModel").newInstance()
    } catch (ClassNotFoundException exception) {
      return null
    }
  }

  static DubboBootstrap newDubboBootstrap(Object frameworkModel) {
    if (frameworkModel == null) {
      return DubboBootstrap.newInstance()
    }
    return DubboBootstrap.newInstance(frameworkModel)
  }
}
