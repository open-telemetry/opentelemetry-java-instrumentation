/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.data.annotation.Id;

public record Customer(@Id String id, String firstName, String lastName) {}
