/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.instrumentation.spring.autoconfigure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configures OpenTelemetry instrumentation tools and interceptors Wraps
 * org.springframework.context.annotation.Configuration and
 * org.springframework.context.annotation.ComponentScan annotations <br>
 * Enables OpenTelemetry Tracing in spring applications by completing a component scan of the
 * autoconfigure trace module
 *
 * @since 0.5.0
 */
@Configuration
@ComponentScan(basePackages = "io.opentelemetry.spring.autoconfigure.trace")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableOpenTelemetryTracing {}
