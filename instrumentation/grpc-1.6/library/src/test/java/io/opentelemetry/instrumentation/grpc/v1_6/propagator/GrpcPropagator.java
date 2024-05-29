/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.propagator;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GrpcPropagator implements TextMapPropagator {

    static final String FIELD = "X-grpc-field";
    private static final String METADATA = "value";
    private static final List<String> FIELDS =
            Collections.unmodifiableList(Arrays.asList(FIELD));

    @Override
    public List<String> fields() {
        return FIELDS;
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
