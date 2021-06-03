package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0;

import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Response;

public class JettyClientWrapUtil {

  public static List<Response.ResponseListener> wrapAndStartTracer(
      Context parentContext, HttpRequest request, List<Response.ResponseListener> listeners) {

    //    Context context =
    //        (requestInterceptor.getCtx() != null) ? requestInterceptor.getCtx() : parentContext;

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
