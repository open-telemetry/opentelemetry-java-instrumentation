/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sampler.internal;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.ParentBasedSamplerModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SamplerModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.TracerProviderModel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Warns when the bundled declarative rule-based routing sampler is selected.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RuleBasedRoutingSamplerDeprecationCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  private static final Logger logger =
      Logger.getLogger(RuleBasedRoutingSamplerDeprecationCustomizerProvider.class.getName());

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    AtomicBoolean warned = new AtomicBoolean();
    customizer.addModelCustomizer(
        model -> {
          if (isSelected(model) && warned.compareAndSet(false, true)) {
            logger.warning(
                "The declarative rule_based_routing sampler is deprecated in the Java agent and"
                    + " Spring Boot starter and will be removed in 3.0. Consider migrating to the"
                    + " SDK incubator composite/development rule_based sampler; its configuration"
                    + " and matching behavior differ.");
          }
          return model;
        });
  }

  private static boolean isSelected(OpenTelemetryConfigurationModel model) {
    TracerProviderModel tracerProvider = model.getTracerProvider();
    return tracerProvider != null && containsRuleBasedRouting(tracerProvider.getSampler());
  }

  private static boolean containsRuleBasedRouting(@Nullable SamplerModel sampler) {
    if (sampler == null) {
      return false;
    }
    if (sampler.getExtensionProperties().containsKey("rule_based_routing")) {
      return true;
    }
    ParentBasedSamplerModel parentBased = sampler.getParentBased();
    return parentBased != null
        && (containsRuleBasedRouting(parentBased.getRoot())
            || containsRuleBasedRouting(parentBased.getRemoteParentSampled())
            || containsRuleBasedRouting(parentBased.getRemoteParentNotSampled())
            || containsRuleBasedRouting(parentBased.getLocalParentSampled())
            || containsRuleBasedRouting(parentBased.getLocalParentNotSampled()));
  }

  @Override
  public int order() {
    return Integer.MAX_VALUE;
  }
}
