import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("net.ltgt.errorprone")
}

dependencies {
  errorprone("com.google.errorprone:error_prone_core")
}

val disableErrorProne = gradle.startParameter.projectProperties.get("disableErrorProne")?.toBoolean()
  ?: false

tasks {
  withType<JavaCompile>().configureEach {
    options.errorprone {
      isEnabled.set(!disableErrorProne)

      disableWarningsInGeneratedCode.set(true)
      allDisabledChecksAsWarnings.set(true)

      excludedPaths.set(".*/build/generated/.*|.*/concurrentlinkedhashmap/.*")

      if (System.getenv("CI") == null) {
        disable("SystemOut")
      }

      disable("BooleanParameter")

      // Doesn't work well with Java 8
      disable("FutureReturnValueIgnored")

      // Require Guava
      disable("AutoValueImmutableFields")
      disable("StringSplitter")
      disable("ImmutableMemberCollection")

      // Don't currently use this (to indicate a local variable that's mutated) but could
      // consider for future.
      disable("Var")

      // Don't support Android without desugar
      disable("AndroidJdkLibsChecker")
      disable("Java7ApiChecker")
      disable("StaticOrDefaultInterfaceMethod")

      // Great check, but for bytecode manipulation it's too common to separate over
      // onEnter / onExit
      // TODO(anuraaga): Only disable for auto instrumentation project.
      disable("MustBeClosedChecker")

      // Common to avoid an allocation. Revisit if it's worth opt-in suppressing instead of
      // disabling entirely.
      disable("MixedMutabilityReturnType")

      // We end up using obsolete types if a library we're instrumenting uses them.
      disable("JdkObsolete")
      disable("JavaUtilDate")

      // Limits API possibilities
      disable("NoFunctionalReturnType")

      // Storing into a variable in onEnter triggers this unfortunately.
      // TODO(anuraaga): Only disable for auto instrumentation project.
      disable("UnusedVariable")

      // TODO(anuraaga): Remove this, we use this pattern in several tests and it will mean
      // some moving.
      disable("DefaultPackage")

      // TODO(anuraaga): Remove this, all our advice classes miss constructors but probably should
      // address this.
      disable("PrivateConstructorForUtilityClass")

      // TODO(anuraaga): Remove this, probably after instrumenter API migration instead of dealing
      // with older APIs.
      disable("InconsistentOverloads")
      disable("TypeParameterNaming")

      // We don't use tools that recognize.
      disable("InlineMeSuggester")
      disable("DoNotCallSuggester")

      if (name.contains("Jmh") || name.contains("Test")) {
        disable("HashCodeToString")
        disable("MemberName")
      }
    }
  }
}
