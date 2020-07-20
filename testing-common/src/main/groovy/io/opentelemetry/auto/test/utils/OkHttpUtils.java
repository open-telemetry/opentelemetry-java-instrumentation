/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.test.utils;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class was moved from groovy to java because groovy kept trying to introspect on the
 * OkHttpClient class which contains java 8 only classes, which caused the build to fail for java 7.
 */
public class OkHttpUtils {

  private static final Logger CLIENT_LOGGER = LoggerFactory.getLogger("http-client");

  static {
    ((ch.qos.logback.classic.Logger) CLIENT_LOGGER).setLevel(ch.qos.logback.classic.Level.DEBUG);
  }

  private static final HttpLoggingInterceptor LOGGING_INTERCEPTOR =
      new HttpLoggingInterceptor(
          new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(final String message) {
              CLIENT_LOGGER.debug(message);
            }
          });

  static {
    LOGGING_INTERCEPTOR.setLevel(Level.BASIC);
  }

  static OkHttpClient.Builder clientBuilder() {
    TimeUnit unit = TimeUnit.MINUTES;
    return new OkHttpClient.Builder()
        .addInterceptor(LOGGING_INTERCEPTOR)
        .connectTimeout(1, unit)
        .writeTimeout(1, unit)
        .readTimeout(1, unit);
  }

  public static OkHttpClient client() {
    return client(false);
  }

  public static OkHttpClient client(final boolean followRedirects) {
    return clientBuilder().followRedirects(followRedirects).build();
  }
}
