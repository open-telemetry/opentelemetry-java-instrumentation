package io.opentelemetry.instrumentation.api.tracer.binding;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import io.opentelemetry.api.trace.SpanBuilder;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.Signature;

public enum DefaultTraceBinder implements TraceBinder {
    INSTANCE;

    private static final TraceAttributesBinding IDENTITY = (builder, args) -> builder;

    public TraceBinding bind(Method method, WithSpan annotation) {

        TraceBinding binding;
        if (StringUtils.isNotBlank(annotation.value())) {
            binding = (tracer, joinPoint) -> tracer.spanBuilder(annotation.value())
                    .setSpanKind(annotation.kind());
        } else {
            binding = (tracer, joinPoint) -> {
                Signature signature = joinPoint.getSignature();
                Object target = joinPoint.getTarget();
                String typeName = (target != null) ? target.getClass().getSimpleName() : signature.getDeclaringTypeName();
                String spanName = typeName + "." + signature.getName();
                return tracer.spanBuilder(spanName)
                        .setSpanKind(annotation.kind());
            };
        }
        return bindParameters(binding, method);
    }

    private static TraceBinding bindParameters(TraceBinding traceBinding, Method method) {

        Parameter[] parameters = method.getParameters();
        if (parameters == null || parameters.length == 0) {
            return traceBinding;
        }

        TraceAttributesBinding attributesBinding = bindParameters(parameters, IDENTITY);
        if (attributesBinding == IDENTITY) {
            return traceBinding;
        }
        return (tracer, joinPoint) -> attributesBinding.apply(traceBinding.apply(tracer, joinPoint), joinPoint.getArgs());
    }

    private static TraceAttributesBinding bindParameters(Parameter[] parameters, TraceAttributesBinding attributesBinding) {

        for (int i = 0; i < parameters.length; i++) {
            attributesBinding = bindParameter(parameters[i], i, attributesBinding);
        }
        return attributesBinding;
    }

    private static TraceAttributesBinding bindParameter(Parameter parameter, int index, TraceAttributesBinding attributesBinding) {

        Trace.Attribute annotation = parameter.getAnnotation(WithSpan.Attribute.class);
        if (annotation == null) {
            return attributesBinding;
        }

        String attributeName;
        if (StringUtils.isNotBlank(annotation.value())) {
            attributeName = annotation.value();
        } else if (parameter.isNamePresent()) {
            attributeName = parameter.getName();
        } else {
            return attributesBinding;
        }

        AttributeBinding converter = AttributeBinder
                .bind(attributeName, parameter.getParameterizedType());
        if (converter == null) {
            return attributesBinding;
        }

        return (spanBuilder, args) -> {
            SpanBuilder next = attributesBinding.apply(spanBuilder, args);
            Object arg = args[index];
            if (arg == null) {
                return next;
            }
            return converter.apply(next, arg);
        };
    }

    @FunctionalInterface
    private interface TraceAttributesBinding {
        SpanBuilder apply(SpanBuilder spanBuilder, Object[] args);
    }
}
