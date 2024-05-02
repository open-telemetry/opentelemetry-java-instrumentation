val springBootVersion = System.getenv("SPRING_BOOT_VERSION") ?: "3.2.5"
val springBoot2 = springBootVersion.startsWith("2.")
plugins {
  id("otel.java-conventions")
  id("org.springframework.boot") version (System.getenv("SPRING_BOOT_VERSION") ?: "3.2.5")
  id("org.graalvm.buildtools.native")
}

description = "smoke-tests-otel-starter-spring-boot-3-reactive"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation("org.springframework.boot:spring-boot-starter-webflux")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation(project(":smoke-tests-otel-starter:spring-smoke-testing"))
}

if (springBoot2) {
  configurations.configureEach {
    resolutionStrategy {
      // requires old logback (and therefore also old slf4j)
      force("ch.qos.logback:logback-classic:1.2.13")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}

tasks {
  test {
    // suppress warning about byte-buddy-agent being loaded dynamically
    jvmArgs("-XX:+EnableDynamicAgentLoading")
  }
  compileAotJava { // $requiresSpringBoot3
    with(options) { // $requiresSpringBoot3
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none") // $requiresSpringBoot3
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    } // $requiresSpringBoot3
  } // $requiresSpringBoot3
  compileAotTestJava { // $requiresSpringBoot3
    with(options) { // $requiresSpringBoot3
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none") // $requiresSpringBoot3
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    } // $requiresSpringBoot3
  } // $requiresSpringBoot3
  checkstyleAot { // $requiresSpringBoot3
    isEnabled = false // $requiresSpringBoot3
  } // $requiresSpringBoot3
  checkstyleAotTest { // $requiresSpringBoot3
    isEnabled = false // $requiresSpringBoot3
  } // $requiresSpringBoot3
}

// To be able to execute the tests as GraalVM native executables
configurations.configureEach {
  exclude("org.apache.groovy", "groovy")
  exclude("org.apache.groovy", "groovy-json")
  exclude("org.spockframework", "spock-core")
}

graalvmNative {
  binaries.all {
    // Workaround for https://github.com/junit-team/junit5/issues/3405
    buildArgs.add("--initialize-at-build-time=org.junit.platform.launcher.core.LauncherConfig")
    buildArgs.add("--initialize-at-build-time=org.junit.jupiter.engine.config.InstantiatingConfigurationParameterConverter")
  }

  // See https://github.com/graalvm/native-build-tools/issues/572
  metadataRepository {
    enabled.set(false)
  }

  tasks.test {
    useJUnitPlatform()
    setForkEvery(1)
  }
}
