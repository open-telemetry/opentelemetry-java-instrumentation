/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.internal.Entity;
import io.opentelemetry.sdk.resources.internal.EntityUtil;
import io.opentelemetry.semconv.SchemaUrls;
import javax.annotation.Nullable;

/**
 * Creates service resources with entity associations.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ServiceResource {

  public static Resource create(@Nullable String serviceName, @Nullable String serviceVersion) {
    Attributes description = Attributes.builder().put(SERVICE_VERSION, serviceVersion).build();
    if (serviceName == null) {
      return Resource.create(description);
    }
    return EntityUtil.createResource(
        singletonList(
            Entity.builder("service", Attributes.of(SERVICE_NAME, serviceName))
                .setSchemaUrl(SchemaUrls.V1_40_0)
                .setDescription(description)
                .build()));
  }

  private ServiceResource() {}
}
