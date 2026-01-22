/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

/**
 * Marker interface to indicate that `db.query.summary` should be extracted from `db.query.text`.
 *
 * <p>This is a temporary interface so that we can opt into this behavior across multiple PRs
 * instead of a single large PR.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface ExtractQuerySummaryMarker {}
