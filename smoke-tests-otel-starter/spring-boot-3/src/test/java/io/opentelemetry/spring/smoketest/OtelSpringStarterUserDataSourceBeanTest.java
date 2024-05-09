/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.test.context.ActiveProfiles;

@DisabledInNativeImage // Spring native does not support the profile setting at runtime:
// https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.aot.conditions
@ActiveProfiles(value = "user-has-defined-datasource-bean")
class OtelSpringStarterUserDataSourceBeanTest extends OtelSpringStarterSmokeTest {}
