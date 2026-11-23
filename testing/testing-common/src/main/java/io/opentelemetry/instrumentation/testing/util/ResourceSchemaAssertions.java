/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.ServiceResourceDetector;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.internal.Entity;
import io.opentelemetry.sdk.resources.internal.EntityUtil;
import java.util.List;

public final class ResourceSchemaAssertions {

  public static String getSdkEntitySchemaUrl() {
    // Use Resource.getDefault() once https://github.com/open-telemetry/opentelemetry-java/pull/8927
    // is merged. All SDK entities are assumed to share the same schema URL.
    Resource resource =
        new ServiceResourceDetector()
            .create(DeclarativeConfiguration.toConfigProperties(emptyMap()));
    List<String> schemaUrls =
        EntityUtil.getEntities(resource).stream()
            .map(Entity::getSchemaUrl)
            .distinct()
            .collect(toList());
    assertThat(schemaUrls).hasSize(1).doesNotContainNull();
    return schemaUrls.get(0);
  }

  public static void assertResourceEntitySchemasMatchSdk(Resource resource) {
    String sdkSchemaUrl = getSdkEntitySchemaUrl();
    assertThat(EntityUtil.getEntities(resource))
        .allSatisfy(entity -> assertThat(entity.getSchemaUrl()).isEqualTo(sdkSchemaUrl));
  }

  private ResourceSchemaAssertions() {}
}
