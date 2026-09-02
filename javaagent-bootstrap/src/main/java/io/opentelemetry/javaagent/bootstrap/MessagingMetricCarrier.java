/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;

/** Stores messaging metric claims on carriers across instrumentation and thread boundaries. */
public final class MessagingMetricCarrier {

  public static final Claim CONSUMED_MESSAGES = new Claim();

  private static final Claim[] CLAIMS = {CONSUMED_MESSAGES};

  public static boolean hasClaim(Object carrier, Claim claim) {
    return carrier != null && Boolean.TRUE.equals(claim.claimedCarriers.get(carrier));
  }

  public static void markClaim(Object carrier, Claim claim) {
    if (carrier != null) {
      claim.claimedCarriers.put(carrier, Boolean.TRUE);
    }
  }

  public static void copyClaims(Object source, Object target) {
    if (target == null) {
      return;
    }
    for (Claim claim : CLAIMS) {
      if (hasClaim(source, claim)) {
        markClaim(target, claim);
      } else {
        claim.claimedCarriers.remove(target);
      }
    }
  }

  public static final class Claim {
    private final Cache<Object, Boolean> claimedCarriers = Cache.weak();

    private Claim() {}
  }

  private MessagingMetricCarrier() {}
}
