import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("otel.library-instrumentation")

  id("org.jetbrains.kotlin.jvm")
}

val ktorVersion = "3.0.0"
val ktor343TestVersion = "3.4.3"
val ktor360TestVersion = "3.6.0"

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

// Keep the normal suite on Ktor 3.0.0 and test fixed releases on separate runtime classpaths.
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

val ktor360TestRuntimeClasspath = configurations.create("ktor360TestRuntimeClasspath") {
  extendsFrom(configurations.testRuntimeClasspath.get())
  isCanBeConsumed = false
  isCanBeResolved = true
  resolutionStrategy.eachDependency {
    if (requested.group == "io.ktor") {
      useVersion(ktor360TestVersion)
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

  val ktor300FatJarTestFixture = register<Jar>("ktor300FatJarTestFixture") {
    archiveFileName.set("application-all-ktor300.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fatJarTest"))
    manifest {
      attributes("Implementation-Version" to "1.0.0")
    }
    from({
      configurations.testRuntimeClasspath.get().files
        .filter { it.name.startsWith("ktor-") && it.extension == "jar" }
        .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
  }

  val ktor343FatJarTestFixture = register<Jar>("ktor343FatJarTestFixture") {
    archiveFileName.set("application-all-ktor343.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fatJarTest"))
    from({
      ktor343TestRuntimeClasspath.files
        .filter { it.name.startsWith("ktor-") && it.extension == "jar" }
        .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
  }

  val ktor360GenericManifestFatJarTestFixture = register<Jar>("ktor360GenericManifestFatJarTestFixture") {
    archiveFileName.set("application-all-ktor360-generic.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fatJarTest"))
    manifest {
      attributes("Implementation-Version" to "1.0.0")
    }
    from({
      ktor360TestRuntimeClasspath.files
        .filter { it.name.startsWith("ktor-") && it.extension == "jar" }
        .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
  }

  // Exercise ordinary classpaths and bundled Ktor classes with missing or generic metadata.
  val contextPropagationTestName = "io.opentelemetry.instrumentation.ktor.v3_0.KtorServerContextPropagationTest"
  // The no-dispatcher-hop regression is a pre-existing Ktor 3.0.0 leak; test the supported
  // dispatcher-switch path on 3.0.0 and both paths on fixed Ktor releases.
  val dispatcherSwitchTestName = "$contextPropagationTestName.eachRequestContinuesItsOwnTraceparentAfterDispatcherSwitch"
  val testKtor300 = register<Test>("testKtor300") {
    systemProperty("collectMetadata", false)
    description = "Runs the Ktor context propagation compatibility test against Ktor $ktorVersion."
    if (providers.gradleProperty("testLatestDeps").orNull != "true") {
      systemProperty("otel.test.ktor.regularJarVersion", ktorVersion)
    }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.main.get().output + sourceSets.test.get().output + configurations.testRuntimeClasspath.get()
    filter {
      includeTestsMatching(dispatcherSwitchTestName)
    }
  }

  val testKtor300FatJar = register<Test>("testKtor300FatJar") {
    systemProperty("collectMetadata", false)
    description = "Runs the Ktor context propagation compatibility test against Ktor $ktorVersion with Ktor classes bundled into an application JAR with generic version metadata."
    systemProperty("otel.test.ktor.fatJar", true)
    systemProperty("otel.test.ktor.genericManifestVersion", true)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = files(ktor300FatJarTestFixture.flatMap { it.archiveFile }) + sourceSets.main.get().output + sourceSets.test.get().output + configurations.testRuntimeClasspath.get()
    filter {
      includeTestsMatching(dispatcherSwitchTestName)
    }
  }

  val testKtor343 = register<Test>("testKtor343") {
    systemProperty("collectMetadata", false)
    description = "Runs the Ktor context propagation regression against Ktor $ktor343TestVersion on ordinary dependency JARs."
    if (providers.gradleProperty("testLatestDeps").orNull != "true") {
      systemProperty("otel.test.ktor.regularJarVersion", ktor343TestVersion)
    }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.main.get().output + sourceSets.test.get().output + ktor343TestRuntimeClasspath
    filter {
      includeTestsMatching(contextPropagationTestName)
    }
  }

  val testKtor343FatJar = register<Test>("testKtor343FatJar") {
    systemProperty("collectMetadata", false)
    description = "Runs the Ktor $ktor343TestVersion regression with Ktor classes bundled into a metadata-free application JAR."
    systemProperty("otel.test.ktor.fatJar", true)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = files(ktor343FatJarTestFixture.flatMap { it.archiveFile }) + sourceSets.main.get().output + sourceSets.test.get().output + ktor343TestRuntimeClasspath
    filter {
      includeTestsMatching(contextPropagationTestName)
    }
  }

  val testKtor360GenericManifestFatJar = register<Test>("testKtor360GenericManifestFatJar") {
    systemProperty("collectMetadata", false)
    description = "Runs Ktor $ktor360TestVersion against a fat JAR with misleading application Implementation-Version metadata."
    systemProperty("otel.test.ktor.fatJar", true)
    systemProperty("otel.test.ktor.genericManifestVersion", true)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = files(ktor360GenericManifestFatJarTestFixture.flatMap { it.archiveFile }) + sourceSets.main.get().output + sourceSets.test.get().output + ktor360TestRuntimeClasspath
    filter {
      includeTestsMatching(contextPropagationTestName)
    }
  }

  val contextPropagationTest = "**/KtorServerContextPropagationTest.class"
  named<Test>("test") { exclude(contextPropagationTest) }
  testExperimental.configure { exclude(contextPropagationTest) }
  testPreviewSemconv.configure { exclude(contextPropagationTest) }

  check {
    dependsOn(testExperimental, testPreviewSemconv, testKtor300, testKtor300FatJar, testKtor343, testKtor343FatJar, testKtor360GenericManifestFatJar)
  }
}
