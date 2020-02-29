package io.opentelemetry.auto.typed.tracer;

import io.opentelemetry.auto.typed.span.HttpClientTypedSpan;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpClientTypedTracer<
        T extends HttpClientTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends ClientTypedTracer<T, REQUEST, RESPONSE> {}
