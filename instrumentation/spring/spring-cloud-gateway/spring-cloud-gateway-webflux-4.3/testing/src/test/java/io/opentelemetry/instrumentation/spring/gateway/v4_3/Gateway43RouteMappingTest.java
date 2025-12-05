/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.v4_3;

import io.opentelemetry.instrumentation.spring.gateway.common.AbstractRouteMappingTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {Gateway43TestApplication.class})
class Gateway43RouteMappingTest extends AbstractRouteMappingTest {}
