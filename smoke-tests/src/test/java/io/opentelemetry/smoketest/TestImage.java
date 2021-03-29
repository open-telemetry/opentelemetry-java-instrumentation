/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

public class TestImage {
  public final Platform platform;
  public final String imageName;

  public TestImage(Platform platform, String imageName) {
    this.platform = platform;
    this.imageName = imageName;
  }

  @Override
  public String toString() {
    return imageName + "(" + platform + ")";
  }

  public static TestImage linuxImage(String imageName) {
    return new TestImage(Platform.LINUX_X86_64, imageName);
  }

  public static TestImage windowsImage(String imageName) {
    return new TestImage(Platform.WINDOWS_X86_64, imageName);
  }

  public enum Platform {
    WINDOWS_X86_64,
    LINUX_X86_64,
  }
}
