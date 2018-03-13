package datadog.trace.instrumentation.jaxrs;

import datadog.trace.api.Trace;
import java.util.concurrent.Future;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TracedInvocation implements Invocation {
  private final Invocation wrapped;

  public TracedInvocation(final Invocation wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public Invocation property(final String name, final Object value) {
    return wrapped.property(name, value);
  }

  @Trace
  @Override
  public Response invoke() {
    return wrapped.invoke();
  }

  @Trace
  @Override
  public <T> T invoke(final Class<T> responseType) {
    return wrapped.invoke(responseType);
  }

  @Trace
  @Override
  public <T> T invoke(final GenericType<T> responseType) {
    return wrapped.invoke(responseType);
  }

  @Override
  public Future<Response> submit() {
    return null;
  }

  @Override
  public <T> Future<T> submit(final Class<T> responseType) {
    return null;
  }

  @Override
  public <T> Future<T> submit(final GenericType<T> responseType) {
    return null;
  }

  @Override
  public <T> Future<T> submit(final InvocationCallback<T> callback) {
    return null;
  }
}
