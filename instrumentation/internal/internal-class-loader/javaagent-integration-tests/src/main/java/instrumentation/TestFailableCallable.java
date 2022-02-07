/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import org.apache.commons.lang3.function.FailableCallable;

public interface TestFailableCallable<R, E extends Throwable> extends FailableCallable<R, E> {}
