/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7

import io.opentelemetry.instrumentation.test.AgentTestTrait
import spock.lang.IgnoreIf

// TODO (trask) fix the test on latest version of dubbo
@IgnoreIf({ Boolean.getBoolean("testLatestDeps") })
class DubboTraceChainTest extends AbstractDubboTraceChainTest implements AgentTestTrait {
}
