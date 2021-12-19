/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jboss.logmanager.LogContext

class JBossJavaUtilLoggingTest extends JavaUtilLoggingTest {

  @Override
  Object createLogger(String name) {
    LogContext.create().getLogger(name)
  }
}
