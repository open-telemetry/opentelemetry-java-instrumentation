package datadog.trace.instrumentation.netty38;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class AbstractNettyAdvice {
  public static void muzzleCheck(final HttpRequest httpRequest) {
    final HttpHeaders headers = httpRequest.headers();
  }
}
