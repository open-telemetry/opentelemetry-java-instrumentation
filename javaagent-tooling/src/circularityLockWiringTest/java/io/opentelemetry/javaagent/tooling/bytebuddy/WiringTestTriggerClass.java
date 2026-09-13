/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

/**
 * Loaded only via {@link Class#forName} from {@link TransformationTrackingWiringTest}, never
 * referenced directly - see that class for why.
 */
final class WiringTestTriggerClass {}
