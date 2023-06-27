package io.opentelemetry.javaagent.instrumentation.apachecamel;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public abstract class RetryOnAddressAlreadyInUse {
  private static final Logger logger = LoggerFactory.getLogger(RetryOnAddressAlreadyInUse.class);

  protected static void withRetryOnAddressAlreadyInUse(Runnable closure) {
    withRetryOnAddressAlreadyInUse(closure, 3);
  }

  static void withRetryOnAddressAlreadyInUse(Runnable closure, int numRetries) {
    try {
      closure.run();
    } catch (Throwable t) {
      if (numRetries == 0 || t.getMessage() == null || !t.getMessage().contains("Address already in use")) {
        throw t;
      }
      logger.debug("retrying due to bind exception: {}", t.getMessage(), t);
      withRetryOnAddressAlreadyInUse(closure, numRetries - 1);
    }
  }
}
