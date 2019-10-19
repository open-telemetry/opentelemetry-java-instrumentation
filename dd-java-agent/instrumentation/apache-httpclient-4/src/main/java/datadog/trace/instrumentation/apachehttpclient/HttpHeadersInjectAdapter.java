package datadog.trace.instrumentation.apachehttpclient;

import datadog.trace.instrumentation.api.AgentPropagation;
import org.apache.http.client.methods.HttpUriRequest;

// not sure why, but gradle build fails without fully qualified HttpUriRequest class name
public class HttpHeadersInjectAdapter implements AgentPropagation.Setter<HttpUriRequest> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(final HttpUriRequest carrier, final String key, final String value) {
    carrier.addHeader(key, value);
  }
}
