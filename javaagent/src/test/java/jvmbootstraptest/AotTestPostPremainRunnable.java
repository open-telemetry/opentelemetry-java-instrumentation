/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

public class AotTestPostPremainRunnable implements Runnable {

  @Override
  public void run() {
    if (!AotTestApplication.hasInjectedField(getClass())) {
      throw new IllegalStateException("Field not injected into post-premain class");
    }
    System.out.println("AOT_POST_PREMAIN_CLASS_FIELD_BACKED");
  }
}
