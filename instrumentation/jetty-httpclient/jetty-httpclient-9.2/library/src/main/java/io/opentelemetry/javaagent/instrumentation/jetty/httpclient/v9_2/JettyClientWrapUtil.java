package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;
import org.eclipse.jetty.client.api.Response;

public class JettyClientWrapUtil {

  /**
   * Utility to wrap the response listeners only, this includes the important CompleteListener
   *
   * @param parentContext top level context that is above the Jetty client span context
   * @param listeners all listeners passed to Jetty client send() method
   * @return list of wrapped ResponseListeners
   */
  public static List<Response.ResponseListener> wrapResponseListeners(
      Context parentContext, List<Response.ResponseListener> listeners) {

    List<Response.ResponseListener> wrapped =
        listeners.stream()
            .map(
                listener -> {
                  Response.ResponseListener wrappedListener = listener;
                  if (listener instanceof Response.CompleteListener
                      && !(listener instanceof JettyHttpClient9TracingInterceptor)) {
                    wrappedListener =
                        (Response.CompleteListener)
                            result -> {
                              try (Scope ignored = parentContext.makeCurrent()) {
                                ((Response.CompleteListener) listener).onComplete(result);
                              }
                            };
                  }
                  return wrappedListener;
                })
            .collect(toList());
    return wrapped;
  }
}
