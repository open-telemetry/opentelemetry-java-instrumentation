package datadog.trace.agent.test.utils;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/**
 * This class was moved from groovy to java because groovy kept trying to introspect on the
 * OkHttpClient class which contains java 8 only classes, which caused the build to fail for java 7.
 */
public class OkHttpUtils {

  static OkHttpClient.Builder clientBuilder() {
    final TimeUnit unit = TimeUnit.MINUTES;
    return new OkHttpClient.Builder()
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
