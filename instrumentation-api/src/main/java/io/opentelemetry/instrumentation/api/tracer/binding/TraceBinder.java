package io.opentelemetry.instrumentation.api.tracer.binding;

import java.lang.reflect.Method;

public interface TraceBinder {
    TraceBinding bind(Method method, WithSpan annotation);
}
