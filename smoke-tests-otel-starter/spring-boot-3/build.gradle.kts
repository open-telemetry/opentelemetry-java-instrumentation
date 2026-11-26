import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.springframework.boot.gradle.tasks.aot.ProcessTestAot

plugins {
  id("otel.java-conventions")
  id("org.springframework.boot") version "3.5.16"
  id("otel.spring-native-test-conventions")
  id("otel.testcontainers-testing")
}

description = "smoke-tests-otel-starter-spring-boot-3"

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
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-mongodb")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(project(":instrumentation:spring:spring-boot-autoconfigure"))
}

val testDeclarativeConfig = testing.suites.register<JvmTestSuite>("testDeclarativeConfig") {
  dependencies {
    implementation(project())
    implementation(project(":smoke-tests-otel-starter:spring-boot-common"))
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-test")
    // The AOT compiler uses the test runtime classpath and needs JUnit's API Guardian annotations.
    runtimeOnly("org.apiguardian:apiguardian-api:1.1.2")
    runtimeOnly("org.junit.platform:junit-platform-launcher")
  }
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication"
}

tasks {
  bootJar {
    enabled = false
  }
  check {
    dependsOn(testing.suites)
  }
}

plugins.withId("org.graalvm.buildtools.native") {
  val suite = testDeclarativeConfig.get().sources
  val processAot = tasks.register<ProcessTestAot>("processTestDeclarativeConfigAot") {
    classpath = suite.runtimeClasspath
    classpathRoots = suite.output
    groupId.set(project.group.toString())
    artifactId.set(project.name)
    sourcesOutput.set(layout.buildDirectory.dir("generated/aotDeclarativeConfigSources"))
    resourcesOutput.set(layout.buildDirectory.dir("generated/aotDeclarativeConfigResources"))
    classesOutput.set(layout.buildDirectory.dir("generated/aotDeclarativeConfigClasses"))
  }
  val compileAot = tasks.register<JavaCompile>("compileAotDeclarativeConfigJava") {
    source(processAot.flatMap { it.sourcesOutput })
    classpath = suite.runtimeClasspath + files(processAot.flatMap { it.classesOutput })
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/aotDeclarativeConfig"))
    options.compilerArgs.add("-Xlint:-deprecation,-unchecked,-rawtypes,none")
  }

  extensions.configure<GraalVMExtension>("graalvmNative") {
    // GraalVM uses the native<Name> run task for binaries whose names end in Test.
    registerTestBinary("declarativeConfigTest") {
      usingSourceSet(suite)
      forTestTask(tasks.named<Test>("testDeclarativeConfig"))
    }
    binaries.named("declarativeConfigTest") {
      quickBuild.set(true)
      classpath(compileAot.flatMap { it.destinationDirectory })
      classpath(processAot.flatMap { it.classesOutput }, processAot.flatMap { it.resourcesOutput })
    }
  }
  tasks.named("nativeDeclarativeConfigTestCompile") {
    dependsOn("extractRepositoryMetadata")
  }
  tasks.named("nativeTest") {
    dependsOn("nativeDeclarativeConfigTest")
  }
}
