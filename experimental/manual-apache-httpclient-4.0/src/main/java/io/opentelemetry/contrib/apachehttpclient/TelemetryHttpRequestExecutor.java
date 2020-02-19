package io.opentelemetry.contrib.apachehttpclient;

import io.opentelemetry.helpers.apachehttpclient.ApacheHttpClientSpanDecorator;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;

public class TelemetryHttpRequestExecutor extends HttpRequestExecutor {

  private final ApacheHttpClientSpanDecorator decorator;

  public TelemetryHttpRequestExecutor(final ApacheHttpClientSpanDecorator decorator) {
    super();
    this.decorator = decorator;
  }

  @Override
  public HttpResponse execute(
      final HttpRequest request, final HttpClientConnection conn, final HttpContext context)
      throws IOException, HttpException {
    if (request instanceof HttpUriRequest) {
      HttpUriRequest uriRequest = (HttpUriRequest) request;
      try {
        return decorator.callWithTelemetry(
            "http.request",
            uriRequest,
            uriRequest,
            new Callable<HttpResponse>() {
              @Override
              public HttpResponse call() throws Exception {
                return doInternalExecute(request, conn, context);
              }
            });
      } catch (IOException cause) {
        throw cause;
      } catch (HttpException cause) {
        throw cause;
      } catch (RuntimeException cause) {
        throw cause;
      } catch (Exception cause) {
        throw new HttpException("unexpected exception", cause);
      }
    } else {
      return doInternalExecute(request, conn, context);
    }
  }

  private HttpResponse doInternalExecute(
      HttpRequest request, HttpClientConnection conn, HttpContext context)
      throws IOException, HttpException {
    return super.execute(request, conn, context);
  }
}
