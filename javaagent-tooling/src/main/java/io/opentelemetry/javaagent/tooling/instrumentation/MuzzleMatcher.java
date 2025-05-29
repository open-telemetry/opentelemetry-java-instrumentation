/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TransformSafeLogger;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.config.AgentConfig;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.InstrumentationModuleClassLoader;
import io.opentelemetry.javaagent.tooling.muzzle.Mismatch;
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceMatcher;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;

/**
 * A ByteBuddy matcher that decides whether this instrumentation should be applied. Calls generated
 * {@link ReferenceMatcher}: if any mismatch with the passed {@code classLoader} is found this
 * instrumentation is skipped.
 */
class MuzzleMatcher implements AgentBuilder.RawMatcher {

  private static final Logger muzzleLogger = Logger.getLogger(MuzzleMatcher.class.getName());

  private final TransformSafeLogger instrumentationLogger;
  private final InstrumentationModule instrumentationModule;
  private final Level muzzleLogLevel;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final Cache<ClassLoader, Boolean> matchCache = Cache.weak();
  private volatile ReferenceMatcher referenceMatcher;

  MuzzleMatcher(
      TransformSafeLogger instrumentationLogger,
      InstrumentationModule instrumentationModule,
      ConfigProperties config) {
    this.instrumentationLogger = instrumentationLogger;
    this.instrumentationModule = instrumentationModule;
    this.muzzleLogLevel = AgentConfig.isDebugModeEnabled(config) ? WARNING : FINE;
  }

  @Override
  public boolean matches(
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain) {
    if (classLoader == BOOTSTRAP_LOADER) {
      classLoader = Utils.getBootstrapProxy();
    }
    if (instrumentationModule.isIndyModule()) {
      return matchCache.computeIfAbsent(
          classLoader,
          cl -> {
            InstrumentationModuleClassLoader moduleCl =
                IndyModuleRegistry.createInstrumentationClassLoaderWithoutRegistration(
                    instrumentationModule, cl);
            return doesMatch(moduleCl);
          });
    } else {
      return matchCache.computeIfAbsent(classLoader, this::doesMatch);
    }
  }

  private boolean doesMatch(ClassLoader classLoader) {
    ReferenceMatcher muzzle = getReferenceMatcher();
    boolean isMatch = muzzle.matches(classLoader);

    if (!isMatch) {
      MuzzleFailureCounter.inc();
      if (muzzleLogger.isLoggable(muzzleLogLevel)) {
        muzzleLogger.log(
            muzzleLogLevel,
            "Instrumentation skipped, mismatched references were found: {0} [class {1}] on {2}",
            new Object[] {
              instrumentationModule.instrumentationName(),
              instrumentationModule.getClass().getName(),
              classLoader
            });
        List<Mismatch> mismatches = muzzle.getMismatchedReferenceSources(classLoader);
        for (Mismatch mismatch : mismatches) {
          muzzleLogger.log(muzzleLogLevel, "-- {0}", mismatch);
        }
      }
    } else {
      if (instrumentationLogger.isLoggable(FINE)) {
        instrumentationLogger.log(
            FINE,
            "Applying instrumentation: {0} [class {1}] on {2}",
            new Object[] {
              instrumentationModule.instrumentationName(),
              instrumentationModule.getClass().getName(),
              classLoader
            });
      }
    }

    return isMatch;
  }

  // ReferenceMatcher is lazily created to avoid unnecessarily loading the muzzle references from
  // the module during the agent setup
  private ReferenceMatcher getReferenceMatcher() {
    if (initialized.compareAndSet(false, true)) {
      referenceMatcher = ReferenceMatcher.of(instrumentationModule);
    }
    return referenceMatcher;
  }
}
