/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;

public class ExceptionRequestListener implements ServletRequestListener {

  @Override
  public void requestDestroyed(ServletRequestEvent sre) {
    if ("true".equals(sre.getServletRequest().getParameter("throwOnRequestDestroyed"))) {
      throw new IllegalStateException("This is expected");
    }
  }

  @Override
  public void requestInitialized(ServletRequestEvent sre) {
    if ("true".equals(sre.getServletRequest().getParameter("throwOnRequestInitialized"))) {
      throw new IllegalStateException("This is expected");
    }
  }
}
