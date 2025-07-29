import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("net.ltgt.errorprone")
}

dependencies {
  errorprone("com.google.errorprone:error_prone_core")
  errorprone(project(":custom-checks"))
}

val disableErrorProne = properties["disableErrorProne"]?.toString()?.toBoolean() ?: false
val testLatestDeps = gradle.startParameter.projectProperties["testLatestDeps"] == "true"

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      errorprone {
        if (disableErrorProne) {
          logger.warn("Errorprone has been disabled. Build may not result in a valid PR build.")
          isEnabled.set(false)
        }

        disableWarningsInGeneratedCode.set(true)
        allDisabledChecksAsWarnings.set(true)

        // Ignore warnings for generated and vendored classes
        excludedPaths.set(".*/build/generated/.*|.*/concurrentlinkedhashmap/.*")

        // it's very convenient to debug stuff in the javaagent using System.out.println
        // and we don't want to conditionally only check this in CI
        // because then the remote gradle cache won't work for local builds
        // so we check this via checkstyle instead
        disable("SystemOut")

        disable("BooleanParameter")

        // We often override a method returning Iterable which this makes tedious
        // for questionable value.
        disable("PreferredInterfaceType")

        // Doesn't work well with Java 8
        disable("FutureReturnValueIgnored")

        // Still Java 8
        disable("Varifier")

        // Doesn't currently use Var annotations.
        disable("Var") // "-Xep:Var:OFF"

        // ImmutableRefactoring suggests using com.google.errorprone.annotations.Immutable,
        // but currently uses javax.annotation.concurrent.Immutable
        disable("ImmutableRefactoring")

        // AutoValueImmutableFields suggests returning Guava types from API methods
        disable("AutoValueImmutableFields")
        // Suggests using Guava types for fields but we don't use Guava
        disable("ImmutableMemberCollection")

        // Fully qualified names may be necessary when deprecating a class to avoid
        // deprecation warning.
        disable("UnnecessarilyFullyQualified")

        // TODO (trask) use animal sniffer
        disable("Java8ApiChecker")
        disable("AndroidJdkLibsChecker")

        // apparently disabling android doesn't disable this
        disable("StaticOrDefaultInterfaceMethod")

        // We don't depend on Guava so use normal splitting
        disable("StringSplitter")

        // Prevents lazy initialization
        disable("InitializeInline")

        // Seems to trigger even when a deprecated method isn't called anywhere.
        // We don't get much benefit from it anyways.
        disable("InlineMeSuggester")

        disable("DoNotCallSuggester")

        // We have nullaway so don't need errorprone nullable checks which have more false positives.
        disable("FieldMissingNullable")
        disable("ParameterMissingNullable")
        disable("ReturnMissingNullable")
        disable("VoidMissingNullable")

        // allow UPPERCASE type parameter names
        disable("TypeParameterNaming")

        // In bytecode instrumentation it's very common to separate across onEnter / onExit
        // TODO: Only disable for javaagent instrumentation modules.
        disable("MustBeClosedChecker")

        // Common to avoid an allocation. Revisit if it's worth opt-in suppressing instead of
        // disabling entirely.
        disable("MixedMutabilityReturnType")

        // We end up using obsolete types if a library we're instrumenting uses them.
        disable("JdkObsolete")
        disable("JavaUtilDate")

        // TODO: Remove this, we use this pattern in several tests and it will mean some moving.
        disable("DefaultPackage")

        // we use modified Otel* checks which ignore *Advice classes
        disable("PrivateConstructorForUtilityClass")
        disable("CanIgnoreReturnValueSuggester")

        // TODO: Remove this, probably after instrumenter API migration instead of dealing with
        // older APIs.
        disable("InconsistentOverloads")

        // lots of low level APIs use arrays
        disable("AvoidObjectArrays")

        disable("BanClassLoader")

        // YodaConditions may improve safety in some cases. The argument of increased
        // cognitive load is dubious.
        disable("YodaCondition")

        disable("NonFinalStaticField")

        // Requires adding compile dependency to JSpecify
        disable("AddNullMarkedToPackageInfo")

        if (testLatestDeps) {
          // Some latest dep tests are compiled for java 17 although the base version uses an older
          // version. Disable rules that suggest using new language features.
          disable("StatementSwitchToExpressionSwitch")
          disable("PatternMatchingInstanceof")
        }

        if (name.contains("Jmh") || name.contains("Test")) {
          // Allow underscore in test-type method names
          disable("MemberName")
        }
        if ((project.path.endsWith(":testing") || name.contains("Test")) && !project.name.equals("custom-checks")) {
          // This check causes too many failures, ignore the ones in tests
          disable("OtelCanIgnoreReturnValueSuggester")
          disable("OtelInternalJavadoc")
        }
      }
    }
  }
}
