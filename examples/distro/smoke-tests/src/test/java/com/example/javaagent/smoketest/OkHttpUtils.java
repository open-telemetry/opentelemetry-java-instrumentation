/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.smoketest;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

public class OkHttpUtils {

  static OkHttpClient.Builder clientBuilder() {
    TimeUnit unit = MINUTES;
    return new OkHttpClient.Builder()
        .connectTimeout(1, unit)
        .writeTimeout(1, unit)
        .readTimeout(1, unit);
  }

  public static OkHttpClient client() {
    return client(false);
  }

  public static OkHttpClient client(boolean followRedirects) {
    return clientBuilder().followRedirects(followRedirects).build();
  }
}
