/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation marks that an execution of this method or constructor is able to add attributes
 * to the current span {@link io.opentelemetry.api.trace.Span}.
 *
 * <p>Application developers can use this annotation to signal OpenTelemetry auto-instrumentation
 * that attributes annotated with the {@link
 * io.opentelemetry.instrumentation.annotations.SpanAttribute} should be detected and added to the
 * current span.
 *
 * <p>If no span is currently active no new span will be created, and no attributes will be
 * extracted.
 *
 * <p>Similar to {@link
 * io.opentelemetry.api.trace.Span#setAttribute(io.opentelemetry.api.common.AttributeKey,
 * java.lang.Object) Span.setAttribute() } methods, if the key is already mapped to a value the old
 * value is replaced by the extracted value.
 *
 * <p>If you are a library developer, then probably you should NOT use this annotation, because it
 * is non-functional without some form of auto-instrumentation.
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface AddingSpanAttributes {}
