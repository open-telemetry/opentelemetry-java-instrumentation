plugins {
  id("otel.java-conventions")
  alias(springBoot31.plugins.versions)
}

if (gradle.startParameter.taskNames.any { it.contains("nativeTest") }) {
  apply(plugin = "org.graalvm.buildtools.native")
}

description = "smoke-tests-otel-starter-spring-boot-reactive-3"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation(project(":smoke-tests-otel-starter:spring-boot-reactive-common"))
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  runtimeOnly("com.h2database:h2")
  runtimeOnly("io.r2dbc:r2dbc-h2")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelReactiveSpringStarterSmokeTestApplication"
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
