import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jetbrains.kotlinx")
    module.set("kotlinx-coroutines-core")
    versions.set("[1.0.0,1.3.8)")
    excludeInstrumentationName("kotlinx-coroutines-flow")
    extraDependency(project(":instrumentation-annotations"))
    extraDependency("io.opentelemetry:opentelemetry-api:1.27.0")
  }
  // 1.3.9 (and beyond?) have changed how artifact names are resolved due to multiplatform variants
  pass {
    group.set("org.jetbrains.kotlinx")
    module.set("kotlinx-coroutines-core-jvm")
    versions.set("[1.3.9,)")
    assertInverse.set(true)
    excludeInstrumentationName("kotlinx-coroutines-flow")
    extraDependency(project(":instrumentation-annotations"))
    extraDependency("io.opentelemetry:opentelemetry-api:1.27.0")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directives above document the range
    name.set("Kotlin Coroutines Flow core")
    group.set("org.jetbrains.kotlinx")
    module.set("kotlinx-coroutines-core")
    versions.set("[1.3.0,1.3.8)")
    excludeInstrumentationName("kotlinx-coroutines-1.0")
    excludeInstrumentationName("kotlinx-coroutines-opentelemetry-instrumentation-annotations")
    excludeInstrumentationName("opentelemetry-instrumentation-annotations-1.16")
  }
  // 1.3.9 (and beyond?) have changed how artifact names are resolved due to multiplatform variants
  pass {
    // instrumentation-docs:ignore - verification only, the directives above document the range
    name.set("Kotlin Coroutines Flow JVM")
    group.set("org.jetbrains.kotlinx")
    module.set("kotlinx-coroutines-core-jvm")
    versions.set("[1.3.9,)")
    assertInverse.set(true)
    excludeInstrumentationName("kotlinx-coroutines-1.0")
    excludeInstrumentationName("kotlinx-coroutines-opentelemetry-instrumentation-annotations")
    excludeInstrumentationName("opentelemetry-instrumentation-annotations-1.16")
  }
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-extension-kotlin")
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.0")
  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
  compileOnly(project(":instrumentation-annotations-support"))
  compileOnly(project(":opentelemetry-instrumentation-annotations-shaded-for-instrumenting", configuration = "shadow"))

  implementation("org.ow2.asm:asm-tree")
  implementation("org.ow2.asm:asm-util")
  implementation(project(":instrumentation:kotlinx-coroutines:kotlinx-coroutines-1.0:javaagent-kotlin"))
  implementation(project(":instrumentation:opentelemetry-instrumentation-annotations-1.16:javaagent"))

  testInstrumentation(project(":instrumentation:opentelemetry-extension-kotlin-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-extension-kotlin")
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
  testImplementation(project(":instrumentation-annotations"))

  testLibrary("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.0") {
    version {
      strictly("1.0.0")
    }
  }
  testLibrary("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.0.0")
  testLibrary("io.vertx:vertx-lang-kotlin-coroutines:3.6.0")
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    // generate metadata for Java 1.8 reflection on method parameters, used in @WithSpan tests
    javaParameters = true
  }
}

testing {
  suites {
    listOf("unitTests", "v3PreviewUnitTests").forEach { suiteName ->
      register<JvmTestSuite>(suiteName) {
        if (suiteName != "unitTests") {
          sources.java.setSrcDirs(listOf("src/unitTests/java"))
          targets.all {
            testTask.configure {
              jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
            }
          }
        }
        dependencies {
          implementation(project())
          implementation(project(":javaagent-extension-api"))
          implementation(project(":instrumentation-api-incubator"))
          implementation(project(":muzzle"))
          implementation("com.google.guava:guava")
          implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
        }
      }
    }

    register<JvmTestSuite>("version13Test") {
      dependencies {
        implementation(project())
        implementation("io.opentelemetry:opentelemetry-extension-kotlin")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation(
          "org.jetbrains.kotlinx:kotlinx-coroutines-core:${baseVersion("1.3.0").orLatest("+")}",
        )
        implementation(
          "org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${baseVersion("1.3.0").orLatest("+")}",
        )
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
        implementation(project(":instrumentation-annotations"))
      }
    }
  }
}

tasks {
  named("byteBuddyKotlin") {
    enabled = false
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  val selectorTests =
    mapOf(
      "testSelectorsNormalDefaultsOff" to listOf(
        "-Dotel.instrumentation.common.default-enabled=false",
        "-Dtest.flow.enabled=false",
        "-Dtest.core.enabled=false",
      ),
      "testSelectorsNormalFlowDisabled" to listOf(
        "-Dotel.instrumentation.kotlinx-coroutines.enabled=true",
        "-Dotel.instrumentation.kotlinx-coroutines-flow.enabled=false",
        "-Dtest.flow.enabled=false",
      ),
      "testSelectorsNormalFlowVersionEnabled" to listOf(
        "-Dotel.instrumentation.kotlinx-coroutines.enabled=false",
        "-Dotel.instrumentation.kotlinx-coroutines-flow-1.3.enabled=true",
        "-Dtest.core.enabled=false",
      ),
      "testSelectorsNormalFlowVersionDisabled" to listOf(
        "-Dotel.instrumentation.kotlinx-coroutines-flow-1.3.enabled=false",
        "-Dtest.flow.enabled=false",
      ),
      "testSelectorsNormalIgnoresPreviewFlow" to listOf(
        "-Dotel.instrumentation.kotlinx-coroutines-1.0-flow.enabled=false",
      ),
      "testSelectorsNormalIgnoresPreviewCore" to listOf(
        "-Dotel.instrumentation.kotlinx-coroutines-1.0-core.enabled=false",
      ),
      "testSelectorsNormalYaml" to listOf(
        "-Dotel.config.file=$projectDir/src/version13Test/resources/normal-selectors.yaml",
        "-Dtest.flow.enabled=false",
      ),
      "testSelectorsPreviewDefaultsOff" to listOf(
        "-Dotel.instrumentation.common.v3-preview=true",
        "-Dotel.instrumentation.common.default-enabled=false",
        "-Dtest.flow.enabled=false",
        "-Dtest.core.enabled=false",
      ),
      "testSelectorsPreviewFlowEnabled" to listOf(
        "-Dotel.instrumentation.common.v3-preview=true",
        "-Dotel.instrumentation.common.default-enabled=false",
        "-Dotel.instrumentation.kotlinx-coroutines-1.0-flow.enabled=true",
        "-Dtest.core.enabled=false",
      ),
      "testSelectorsPreviewFlowDisabled" to listOf(
        "-Dotel.instrumentation.common.v3-preview=true",
        "-Dotel.instrumentation.kotlinx-coroutines-1.0-flow.enabled=false",
        "-Dtest.flow.enabled=false",
      ),
      "testSelectorsPreviewOwnerWins" to listOf(
        "-Dotel.instrumentation.common.v3-preview=true",
        "-Dotel.instrumentation.kotlinx-coroutines.enabled=true",
        "-Dotel.instrumentation.kotlinx-coroutines-1.0-flow.enabled=false",
      ),
      "testSelectorsPreviewIgnoresNormalFlow" to listOf(
        "-Dotel.instrumentation.common.v3-preview=true",
        "-Dotel.instrumentation.kotlinx-coroutines-flow.enabled=false",
        "-Dotel.instrumentation.kotlinx-coroutines-flow-1.3.enabled=false",
      ),
      "testSelectorsPreviewCoreDisabled" to listOf(
        "-Dotel.instrumentation.common.v3-preview=true",
        "-Dotel.instrumentation.kotlinx-coroutines-1.0-core.enabled=false",
        "-Dtest.core.enabled=false",
      ),
      "testSelectorsPreviewYaml" to listOf(
        "-Dotel.config.file=$projectDir/src/version13Test/resources/preview-selectors.yaml",
        "-Dtest.core.enabled=false",
      ),
      "testSelectorsPreviewYamlPrecedence" to listOf(
        "-Dotel.config.file=$projectDir/src/version13Test/resources/preview-precedence.yaml",
      ),
    ).map { (taskName, arguments) ->
      register<Test>(taskName) {
        testClassesDirs = sourceSets["version13Test"].output.classesDirs
        classpath = sourceSets["version13Test"].runtimeClasspath
        filter {
          includeTestsMatching("*CoroutinesSelectorTest")
        }
        jvmArgs("-Dotel.instrumentation.opentelemetry-instrumentation-annotations.enabled=true")
        jvmArgs(arguments)
      }
    }

  check {
    dependsOn(testing.suites, testV3Preview, selectorTests)
  }
}
