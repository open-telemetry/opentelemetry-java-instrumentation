/**
 * Gradle plugin for developing OpenTelemetry Java agent extensions.
 *
 * This plugin simplifies extension development by:
 * - Applying muzzle-check and muzzle-generation plugins for bytecode safety verification
 * - Automatically applying the java-library plugin (via muzzle plugins)
 * - Auto-configuring required muzzle dependencies (muzzleBootstrap, muzzleTooling, codegen)
 * - Inheriting dependency versions from OpenTelemetry instrumentation BOMs when configured
 *
 * Prerequisites:
 * - For automatic version resolution, apply OpenTelemetry instrumentation BOMs:
 *   implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:VERSION"))
 *   implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:VERSION"))
 *
 * Usage:
 * plugins {
 *   id("io.opentelemetry.instrumentation.javaagent-extension") version "VERSION"
 * }
 *
 * @see <a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/muzzle.md">Muzzle Documentation</a>
 */

plugins {
  id("io.opentelemetry.instrumentation.muzzle-check")
  id("io.opentelemetry.instrumentation.muzzle-generation")
}

// Configure muzzle-specific configurations to inherit from project's main configurations.
// This allows dependency versions to be resolved from BOMs applied to compileClasspath/compileOnly.
// Note: The muzzle plugins automatically apply java-library, which creates these configurations.
afterEvaluate {
  val baseConfig = configurations.findByName("compileClasspath")
    ?: configurations.findByName("compileOnly")

  if (baseConfig != null) {
    configurations.named("muzzleBootstrap").configure {
      extendsFrom(baseConfig)
    }
    configurations.named("muzzleTooling").configure {
      extendsFrom(baseConfig)
    }
    configurations.named("codegen").configure {
      extendsFrom(baseConfig)
    }
  }
}

dependencies {
  // Bootstrap dependencies: Required classes for instrumentation that run in the bootstrap classloader
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations-support")

  // Tooling dependencies: Required for muzzle verification and bytecode analysis
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")

  /*
   * Code generation dependency: Used by the muzzle gradle plugin during code generation.
   * These classes are inspected and traversed during the muzzle reference collection phase
   * to generate bytecode safety checks.
   */
  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
}
