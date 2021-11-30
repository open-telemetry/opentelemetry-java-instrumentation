/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.function.Supplier;

public class CrashEarlyJdk8 {

  public static void main(String... args) {
    System.out.println("start test program");
    CrashEarlyJdk8 crash = new CrashEarlyJdk8();
    crash.test();
    System.out.println("test program completed successfully");
  }

  public void test() {
    // run loop enough times for jit compiler to kick in
    for (int i = 0; i < 10_000; i++) {
      this.bar(this::foo);
    }
  }

  public int foo() {
    return 1;
  }

  @SuppressWarnings("ReturnValueIgnored")
  public void bar(Supplier<Integer> supplier) {
    supplier.get();
  }
}
