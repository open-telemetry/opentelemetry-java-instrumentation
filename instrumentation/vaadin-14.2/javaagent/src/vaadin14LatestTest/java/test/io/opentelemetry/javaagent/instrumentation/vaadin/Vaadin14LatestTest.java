/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.io.opentelemetry.javaagent.instrumentation.vaadin;

import io.opentelemetry.javaagent.instrumentation.vaadin.AbstractVaadin14Test;
import java.io.File;

public class Vaadin14LatestTest extends AbstractVaadin14Test {

  @Override
  protected void prepareVaadinBaseDir(File baseDir) {
    copyResource("/pnpm/package.json", baseDir);
    copyResource("/pnpm/pnpm-lock.yaml", baseDir);
  }
}
