plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

base.archivesName.set("opentelemetry-spring-boot-autoconfigure")
group = "io.opentelemetry.instrumentation"

val springBootVersion =
  "2.7.18" // AutoConfiguration is added in 2.7.0, but can be used with older versions

// r2dbc-proxy is shadowed to prevent org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
// from being loaded by Spring Boot (by the presence of META-INF/services/io.r2dbc.spi.ConnectionFactoryProvider) - even if the user doesn't want to use R2DBC.
sourceSets {
  main {
    val shadedDep = project(":instrumentation:r2dbc-1.0:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow-spring"),
      "builtBy" to ":instrumentation:r2dbc-1.0:library-instrumentation-shaded:extractShadowJarSpring",
    )
  }
  create("javaSpring3") {
    java {
      setSrcDirs(listOf("src/main/javaSpring3"))
    }
  }
  create("javaSpring4") {
    java {
      setSrcDirs(listOf("src/main/javaSpring4"))
    }
  }
}

configurations {
  named("javaSpring3CompileOnly") {
    extendsFrom(configurations["compileOnly"])
  }
  named("javaSpring4CompileOnly") {
    extendsFrom(configurations["compileOnly"])
  }
}

dependencies {
  compileOnly("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:$springBootVersion")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")
  implementation("javax.validation:validation-api")
  compileOnly("org.springframework.kafka:spring-kafka:2.9.0")

  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))
  compileOnly(
    project(
      path = ":instrumentation:r2dbc-1.0:library-instrumentation-shaded",
      configuration = "shadow"
    )
  )
  implementation(project(":instrumentation:spring:spring-kafka-2.7:library"))
  implementation(project(":instrumentation:spring:spring-web:spring-web-3.1:library"))
  implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-5.3:library"))
  compileOnly("javax.servlet:javax.servlet-api:3.1.0")
  implementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.3:library"))
  implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))
  implementation(project(":instrumentation:log4j:log4j-appender-2.17:library"))
  compileOnly("org.apache.logging.log4j:log4j-core:2.17.0")
  implementation(project(":instrumentation:logback:logback-appender-1.0:library"))
  implementation(project(":instrumentation:logback:logback-mdc-1.0:library"))
  compileOnly("ch.qos.logback:logback-classic:1.0.0")
  implementation(project(":instrumentation:jdbc:library"))
  implementation(project(":instrumentation:runtime-telemetry:runtime-telemetry-java8:library"))
  implementation(project(":instrumentation:runtime-telemetry:runtime-telemetry-java17:library"))

  library("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-aop:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-data-mongodb:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-data-r2dbc:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-data-jdbc:$springBootVersion")

  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  implementation(project(":sdk-autoconfigure-support"))
  implementation(project(":declarative-config-bridge"))
  compileOnly("io.opentelemetry:opentelemetry-extension-trace-propagators")
  compileOnly("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
  compileOnly("io.opentelemetry:opentelemetry-exporter-logging")
  compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
  compileOnly("io.opentelemetry:opentelemetry-exporter-zipkin")
  compileOnly(project(":instrumentation-annotations"))

  compileOnly(project(":instrumentation:resources:library"))
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")

  testLibrary("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }

  testImplementation("javax.servlet:javax.servlet-api:3.1.0")
  testImplementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
  testRuntimeOnly("com.h2database:h2:1.4.197")
  testRuntimeOnly("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")

  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation(project(":instrumentation:resources:library"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
  testImplementation("io.opentelemetry:opentelemetry-exporter-logging")
  testImplementation("io.opentelemetry:opentelemetry-exporter-otlp")
  testImplementation("io.opentelemetry:opentelemetry-exporter-zipkin")
  testImplementation(project(":instrumentation-annotations"))
  testImplementation(project(":instrumentation:spring:spring-boot-autoconfigure:testing"))

  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-micrometer-metrics:latest.release")

  // needed for the Spring Boot 3 support
  implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library"))

  // give access to common classes, e.g. InstrumentationConfigUtil
  add("javaSpring3CompileOnly", files(sourceSets.main.get().output.classesDirs))
  add("javaSpring3CompileOnly", "org.springframework.boot:spring-boot-starter-web:3.2.4")
  add("javaSpring3CompileOnly", "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  add(
    "javaSpring3CompileOnly",
    project(":instrumentation:spring:spring-web:spring-web-3.1:library")
  )
  add(
    "javaSpring3CompileOnly",
    project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library")
  )

  // Spring Boot 4
  add("javaSpring4CompileOnly", files(sourceSets.main.get().output.classesDirs))
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-starter-kafka:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-autoconfigure:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-jdbc:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-starter-jdbc:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-restclient:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-starter-data-mongodb:4.0.0")
  add("javaSpring4CompileOnly", "org.springframework.boot:spring-boot-starter-micrometer-metrics:4.0.0")
  add("javaSpring4CompileOnly", project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))
  add("javaSpring4CompileOnly", project(":instrumentation:spring:spring-kafka-2.7:library"))
  add("javaSpring4CompileOnly", project(":instrumentation:mongo:mongo-3.1:library"))
  add("javaSpring4CompileOnly", project(":instrumentation:micrometer:micrometer-1.5:library"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// spring 6 (spring boot 3) requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

val testJavaVersion =
  gradle.startParameter.projectProperties["testJavaVersion"]?.let(JavaVersion::toVersion)
val testSpring3 =
  (testJavaVersion == null || testJavaVersion.compareTo(JavaVersion.VERSION_17) >= 0)

testing {
  suites {
    val testLogbackAppender by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("org.mockito:mockito-inline")
        implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

        implementation(project(":instrumentation:logback:logback-appender-1.0:library"))
        implementation(project(":instrumentation:logback:logback-mdc-1.0:library"))
        // using the same versions as in the spring-boot-autoconfigure
        implementation("ch.qos.logback:logback-classic") {
          version {
            strictly("1.2.11")
          }
        }
        implementation("org.slf4j:slf4j-api") {
          version {
            strictly("1.7.32")
          }
        }
      }
    }

    val testLogbackMissing by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

        implementation("org.slf4j:slf4j-api") {
          version {
            strictly("1.7.32")
          }
        }
      }
    }

    val testSpring2 by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
        implementation(project(":instrumentation-api"))
        implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))
        implementation(project(":instrumentation:spring:spring-boot-autoconfigure:testing"))
        // configure Spring Boot 3.x dependencies for latest dep testing
        val version = if (latestDepTest) "3.+" else springBootVersion
        implementation("org.springframework.boot:spring-boot-starter-test:$version")
        implementation("org.springframework.boot:spring-boot-starter-actuator:$version")
        implementation("org.springframework.boot:spring-boot-starter-web:$version")
        implementation("org.springframework.boot:spring-boot-starter-jdbc:$version")
        implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:$version")
        val springKafkaVersion = if (latestDepTest) "3.+" else "2.9.0"
        implementation("org.springframework.kafka:spring-kafka:$springKafkaVersion")
        implementation("javax.servlet:javax.servlet-api:3.1.0")
        runtimeOnly("com.h2database:h2:1.4.197")
        runtimeOnly("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")
      }
    }

    val testSpring3 by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        val version = if (latestDepTest) "3.+" else "3.2.4"
        implementation("org.springframework.boot:spring-boot-starter-web:$version")
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
        implementation(project(":instrumentation:spring:spring-web:spring-web-3.1:library"))
        implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library"))
        implementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
        implementation("org.springframework.boot:spring-boot-starter-test:$version")
      }
    }

    val testSpring4 by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
        val version = if (latestDepTest) "latest.release" else "4.0.0"
        implementation("org.springframework.boot:spring-boot-starter-jdbc:$version")
        implementation("org.springframework.boot:spring-boot-restclient:$version")
        implementation("org.springframework.boot:spring-boot-starter-kafka:$version")
        implementation("org.springframework.boot:spring-boot-starter-actuator:$version")
        implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:$version")
        implementation("org.springframework.boot:spring-boot-starter-micrometer-metrics:$version")
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation(project(":instrumentation-api"))
        implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))
        implementation(project(":instrumentation:spring:spring-boot-autoconfigure:testing"))
        implementation("org.springframework.boot:spring-boot-starter-test:$version")
        runtimeOnly("com.h2database:h2:1.4.197")
        runtimeOnly("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")
      }
    }

    val testDeclarativeConfig by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-exporter-otlp")
        implementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
          exclude("org.junit.vintage", "junit-vintage-engine")
        }
        implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
      }
    }
  }
}

configurations.configureEach {
  if (name.contains("testLogbackMissing")) {
    exclude("ch.qos.logback", "logback-classic")
  }
}

val buildGraalVmReflectionJson = tasks.register("buildGraalVmReflectionJson") {
  val targetFile = File(
    projectDir,
    "src/main/resources/META-INF/native-image/io.opentelemetry.instrumentation/opentelemetry-spring-boot/reflect-config.json"
  )

  onlyIf { !targetFile.exists() }

  doLast {
    val sourcePackage =
      "io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model"
    val sourcePackagePath = sourcePackage.replace(".", "/")

    val incubatorJar = configurations.compileClasspath.get()
      .filter { it.name.contains("opentelemetry-sdk-extension-incubator") && it.name.endsWith(".jar") }
      .singleFile

    val classes = mutableListOf<String>()
    zipTree(incubatorJar).matching {
      include("$sourcePackagePath/**")
    }.forEach {
      val path = it.path

      val className = path
        .substringAfter(sourcePackagePath)
        .removePrefix("/")
        .removeSuffix(".class")
        .replace("/", ".")
      classes.add("$sourcePackage.$className")
    }

    // write into targetFile in json format
    targetFile.parentFile.mkdirs()
    targetFile.bufferedWriter().use { writer ->
      writer.write("[\n")
      classes.forEachIndexed { index, className ->
        writer.write("  {\n")
        writer.write("    \"name\": \"$className\",\n")
        writer.write("    \"allDeclaredMethods\": true,\n")
        writer.write("    \"allDeclaredFields\": true,\n")
        writer.write("    \"allDeclaredConstructors\": true\n")
        writer.write("  }")
        if (index < classes.size - 1) {
          writer.write(",\n")
        } else {
          writer.write("\n")
        }
      }
      writer.write("]\n")
    }
  }
}

tasks {
  compileJava {
    dependsOn(buildGraalVmReflectionJson)
  }

  compileTestJava {
    options.compilerArgs.add("-parameters")
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", latestDepTest)

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  named<JavaCompile>("compileJavaSpring3Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<JavaCompile>("compileTestSpring2Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<JavaCompile>("compileTestSpring3Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<Test>("testSpring2") {
    isEnabled = testSpring3
  }

  named<Test>("testSpring3") {
    isEnabled = testSpring3
  }

  named<JavaCompile>("compileJavaSpring4Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<JavaCompile>("compileTestSpring4Java") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  named<Test>("testSpring4") {
    isEnabled = testSpring3 // same condition as Spring 3 (requires Java 17+)
  }

  named<Jar>("jar") {
    from(sourceSets["javaSpring3"].output)
    from(sourceSets["javaSpring4"].output)
  }

  named<Jar>("sourcesJar") {
    from(sourceSets["javaSpring3"].java)
    from(sourceSets["javaSpring4"].java)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testing.suites, testStableSemconv)
  }
}
