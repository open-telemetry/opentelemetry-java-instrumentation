/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.jms.annotation.EnableJms;

@ComponentScan
@EnableJms
public class AnnotatedListenerConfig extends AbstractConfig {}
