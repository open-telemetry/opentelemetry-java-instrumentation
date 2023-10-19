/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package indy;

import java.util.concurrent.Callable;
import library.MyProxySuperclass;

public class ProxyMe extends MyProxySuperclass implements Callable<String> {

  @Override
  public String call() {
    return "Hi from ProxyMe";
  }

  public static String staticHello() {
    return "Hi from static";
  }
}
