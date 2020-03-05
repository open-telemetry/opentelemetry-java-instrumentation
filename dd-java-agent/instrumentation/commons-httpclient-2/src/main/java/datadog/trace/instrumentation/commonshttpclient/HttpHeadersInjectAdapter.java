package datadog.trace.instrumentation.commonshttpclient;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;

public class HttpHeadersInjectAdapter implements AgentPropagation.Setter<HttpMethod> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(final HttpMethod carrier, final String key, final String value) {
    carrier.setRequestHeader(new Header(key, value));
  }
}
