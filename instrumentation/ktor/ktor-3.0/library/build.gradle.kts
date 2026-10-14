import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("otel.library-instrumentation")

  id("org.jetbrains.kotlin.jvm")
}

val ktorVersion = "3.0.0"
val ktor343TestVersion = "3.4.3"

dependencies {
  library("io.ktor:ktor-client-core:$ktorVersion")
  library("io.ktor:ktor-server-core:$ktorVersion")

  api(project(":instrumentation:ktor:ktor-common-2.0:library"))
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testImplementation(project(":instrumentation:ktor:ktor-3.0:testing"))
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testLibrary("io.ktor:ktor-server-netty:$ktorVersion")
  testLibrary("io.ktor:ktor-server-call-id:$ktorVersion")
  testLibrary("io.ktor:ktor-client-cio:$ktorVersion")
}

// Keep the normal suite on Ktor 3.0.0 and run this regression separately on the first fixed release.
val ktor343TestRuntimeClasspath = configurations.create("ktor343TestRuntimeClasspath") {
  extendsFrom(configurations.testRuntimeClasspath.get())
  isCanBeConsumed = false
  isCanBeResolved = true
  resolutionStrategy.eachDependency {
    if (requested.group == "io.ktor") {
      useVersion(ktor343TestVersion)
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    languageVersion.set(KotlinVersion.KOTLIN_2_0)
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    // this is used for enabling/disabling tests, library instrumentation doesn't use this flag
    jvmArgs("-Dotel.instrumentation.http.server.emit-experimental-telemetry=true")
    systemProperty("metadataConfig", "otel.instrumentation.http.server.emit-experimental-telemetry=true")
  }

  val testPreviewSemconv = register<Test>("testPreviewSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
  }

  val testKtor343 = register<Test>("testKtor343") {
    description = "Runs the Ktor context propagation regression test against Ktor $ktor343TestVersion."
    systemProperty("collectMetadata", false)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.main.get().output + sourceSets.test.get().output + ktor343TestRuntimeClasspath
    filter {
      includeTestsMatching("io.opentelemetry.instrumentation.ktor.v3_0.KtorServerContextPropagationTest")
    }
  }

  val contextPropagationTest = "**/KtorServerContextPropagationTest.class"
  named<Test>("test") { exclude(contextPropagationTest) }
  testExperimental.configure { exclude(contextPropagationTest) }
  testPreviewSemconv.configure { exclude(contextPropagationTest) }

  check {
    dependsOn(testExperimental, testPreviewSemconv, testKtor343)
  }
}
