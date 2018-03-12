package datadog.trace.api.interceptor;

import java.util.Map;

public interface MutableSpan {
  String getOperationName();

  MutableSpan setOperationName(final String serviceName);

  String getServiceName();

  MutableSpan setServiceName(final String serviceName);

  String getResourceName();

  MutableSpan setResourceName(final String resourceName);

  Integer getSamplingPriority();

  MutableSpan setSamplingPriority(final int newPriority);

  String getSpanType();

  MutableSpan setSpanType(final String type);

  Map<String, Object> getTags();

  MutableSpan setTag(final String tag, final String value);

  MutableSpan setTag(final String tag, final boolean value);

  MutableSpan setTag(final String tag, final Number value);

  Boolean isError();

  MutableSpan setError(boolean value);
}
