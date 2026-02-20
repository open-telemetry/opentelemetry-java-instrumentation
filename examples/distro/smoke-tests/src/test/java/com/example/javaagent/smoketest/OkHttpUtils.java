/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.smoketest;

import static java.util.concurrent.TimeUnit.MINUTES;

import okhttp3.OkHttpClient;

public class OkHttpUtils {

  static OkHttpClient.Builder clientBuilder() {
    return new OkHttpClient.Builder()
        .connectTimeout(1, MINUTES)
        .writeTimeout(1, MINUTES)
        .readTimeout(1, MINUTES);
  }

  public static OkHttpClient client() {
    return client(false);
  }

  public static OkHttpClient client(boolean followRedirects) {
    return clientBuilder().followRedirects(followRedirects).build();
  }
}
