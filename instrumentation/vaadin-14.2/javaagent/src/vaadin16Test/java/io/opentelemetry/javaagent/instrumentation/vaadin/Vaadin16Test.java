/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import java.io.File;

public class Vaadin16Test extends AbstractVaadin16Test {

  @Override
  protected void prepareVaadinBaseDir(File baseDir) {
    copyResource("/pnpm/package.json", baseDir);
    copyResource("/pnpm/pnpm-lock.yaml", baseDir);
  }
}
