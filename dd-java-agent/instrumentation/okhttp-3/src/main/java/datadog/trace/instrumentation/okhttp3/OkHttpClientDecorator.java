package datadog.trace.instrumentation.okhttp3;

import datadog.trace.bootstrap.instrumentation.decorator.HttpClientDecorator;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpClientDecorator extends HttpClientDecorator<Request, Response> {
  public static final OkHttpClientDecorator DECORATE = new OkHttpClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"okhttp", "okhttp-3"};
  }

  @Override
  protected String service() {
    return null;
  }

  @Override
  protected String component() {
    return "okhttp";
  }

  @Override
  protected String method(final Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(final Request httpRequest) {
    return httpRequest.url().uri();
  }

  @Override
  protected Integer status(final Response httpResponse) {
    return httpResponse.code();
  }
}
