/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies.targetcl;

import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingStrategy;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingTarget;

@ClassLoadingStrategy(ClassLoadingTarget.INSTRUMENTATION_ISOLATED)
public class DummyOverride {}
