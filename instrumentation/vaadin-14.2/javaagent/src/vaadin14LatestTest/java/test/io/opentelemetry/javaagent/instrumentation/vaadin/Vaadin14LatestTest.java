/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.io.opentelemetry.javaagent.instrumentation.vaadin;

import io.opentelemetry.javaagent.instrumentation.vaadin.AbstractVaadin14Test;
import java.io.File;
import org.junit.jupiter.api.AfterAll;

public class Vaadin14LatestTest extends AbstractVaadin14Test {
  private File dir;

  @Override
  @SuppressWarnings("checkstyle:SystemOut")
  protected void prepareVaadinBaseDir(File baseDir) {
    dir = baseDir;
    copyResource("/pnpm/package.json", baseDir);
    copyResource("/pnpm/pnpm-lock.yaml", baseDir);
    System.err.println("------ prepareVaadinBaseDir");
    for (File f : dir.listFiles()) {
      System.err.println(f.getName() + " " + f.length());
    }
  }

  @AfterAll
  @SuppressWarnings("checkstyle:SystemOut")
  void debug() {
    System.err.println("------ AfterAll");
    for (File f : dir.listFiles()) {
      System.err.println(f.getName() + " " + f.length());
    }
  }
}
