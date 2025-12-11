/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.v2_2;

import io.opentelemetry.instrumentation.spring.gateway.common.AbstractRouteMappingTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {Gateway22TestApplication.class})
class Gateway22RouteMappingTest extends AbstractRouteMappingTest {}
