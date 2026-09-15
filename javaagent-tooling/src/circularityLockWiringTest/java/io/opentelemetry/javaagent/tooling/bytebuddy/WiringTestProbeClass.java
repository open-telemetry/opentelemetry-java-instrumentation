/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

/**
 * Loaded only via {@link Class#forName} from {@link WiringTestAgentExtension}, never referenced
 * directly - see {@link TransformationTrackingWiringTest} for why.
 */
final class WiringTestProbeClass {}
