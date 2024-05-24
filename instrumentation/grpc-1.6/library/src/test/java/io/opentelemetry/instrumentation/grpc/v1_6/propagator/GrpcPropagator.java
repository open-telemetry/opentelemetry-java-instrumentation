package io.opentelemetry.instrumentation.grpc.v1_6.propagator;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Collections;
import java.util.List;

public class GrpcPropagator implements TextMapPropagator {

    public static final String FIELD = "X-grpc-field";
    private static final String METADATA = "value";

    @Override
    public List<String> fields() {
        return Collections.singletonList(FIELD);
    }

    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
        for (int i = 0; i < 2; i++) {
            setter.set(carrier, FIELD, METADATA);
        }
    }

    @Override
    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
        return context;
    }
}
