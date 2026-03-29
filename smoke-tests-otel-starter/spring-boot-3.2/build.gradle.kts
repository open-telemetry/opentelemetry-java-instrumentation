plugins {
  id("otel.java-conventions")
  alias(springBoot32.plugins.versions)
}

if (gradle.startParameter.taskNames.any { it.contains("nativeTest") }) {
  apply(plugin = "org.graalvm.buildtools.native")
}

description = "smoke-tests-otel-starter-spring-boot-3.2"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

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
  testImplementation("org.springframework.boot:spring-boot-starter-test")

  val testLatestDeps = gradle.startParameter.projectProperties["testLatestDeps"] == "true"
  if (testLatestDeps) {
    // with spring boot 3.5.0 versions of org.mongodb:mongodb-driver-sync and org.mongodb:mongodb-driver-core
    // are not in sync
    testImplementation("org.mongodb:mongodb-driver-sync:latest.release")
  }
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

  // See https://github.com/graalvm/native-build-tools/issues/572
  (extensions.getByName("graalvmNative") as ExtensionAware).extensions
    .configure<org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension> {
      enabled.set(false)
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
