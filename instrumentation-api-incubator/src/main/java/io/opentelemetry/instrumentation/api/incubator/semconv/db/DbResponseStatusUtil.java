package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import javax.annotation.Nullable;

public class DbResponseStatusUtil {
  private DbResponseStatusUtil() {
  }

  @Nullable
  public static String dbResponseStatusCode(int responseStatusCode) {
    return isError(responseStatusCode) ? Integer.toString(responseStatusCode) : null;
  }

  private static boolean isError(int responseStatusCode) {
    return responseStatusCode >= 400
        ||
        // invalid status code, does not exist
        responseStatusCode < 100;
  }
}
