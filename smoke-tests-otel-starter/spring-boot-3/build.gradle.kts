plugins {
  id("otel.java-conventions")
  alias(springBoot31.plugins.versions)
}

if (gradle.startParameter.taskNames.any { it.contains("nativeTest") }) {
  apply(plugin = "org.graalvm.buildtools.native")
}

description = "smoke-tests-otel-starter-spring-boot-3"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

val repositoryMetadata by configurations.creating

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  runtimeOnly("com.h2database:h2")
  implementation("org.apache.commons:commons-dbcp2")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-aop")
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation(project(":smoke-tests-otel-starter:spring-boot-common"))
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-mongodb")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(project(":instrumentation:spring:spring-boot-autoconfigure"))

  val testLatestDeps = gradle.startParameter.projectProperties["testLatestDeps"] == "true"
  if (testLatestDeps) {
    // with spring boot 3.5.0 versions of org.mongodb:mongodb-driver-sync and org.mongodb:mongodb-driver-core
    // are not in sync
    testImplementation("org.mongodb:mongodb-driver-sync:latest.release")
  }

  repositoryMetadata("org.graalvm.buildtools:graalvm-reachability-metadata:1.0.0:repository@zip")
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication"
}

tasks {
  bootJar {
    enabled = false
  }
}

plugins.withId("org.graalvm.buildtools.native") {
  tasks.named<JavaCompile>("compileAotJava").configure {
    with(options) {
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none")
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    }
  }
  tasks.named<JavaCompile>("compileAotTestJava").configure {
    with(options) {
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none")
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    }
  }
  tasks.named("checkstyleAot").configure {
    enabled = false
  }
  tasks.named("checkstyleAotTest").configure {
    enabled = false
  }

  val extractRepositoryMetadata by tasks.registering(Copy::class) {
    inputs.files(repositoryMetadata)
    from({
      zipTree(repositoryMetadata.singleFile)
    })
    into(rootProject.layout.buildDirectory.dir("metadata-repository"))
  }
  tasks.named("nativeTestCompile").configure {
    dependsOn(extractRepositoryMetadata)
  }

  // See https://github.com/graalvm/native-build-tools/issues/572
  (extensions.getByName("graalvmNative") as ExtensionAware).extensions
    .configure<org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension> {
      enabled.set(false)
      // manully set up the metadata repository to avoid resolving the default repository, which
      // currently fails with
      // Resolution of the configuration ':smoke-tests-otel-starter:spring-boot-3:detachedConfiguration1' was attempted without an exclusive lock. This is unsafe and not allowed.
      uri.set(extractRepositoryMetadata.get().destinationDir.toURI())
    }

  tasks.named<Test>("test").configure {
    useJUnitPlatform()
    setForkEvery(1)
  }

  // Disable collectReachabilityMetadata task to avoid configuration isolation issues
  // See https://github.com/gradle/gradle/issues/17559
  tasks.named("collectReachabilityMetadata").configure {
    enabled = false
  }
}
