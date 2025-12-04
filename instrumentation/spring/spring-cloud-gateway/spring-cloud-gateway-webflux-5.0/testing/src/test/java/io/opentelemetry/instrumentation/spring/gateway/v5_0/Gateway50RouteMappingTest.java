/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.v5_0;

import io.opentelemetry.instrumentation.spring.gateway.common.AbstractRouteMappingTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {Gateway50TestApplication.class})
class Gateway50RouteMappingTest extends AbstractRouteMappingTest {}
