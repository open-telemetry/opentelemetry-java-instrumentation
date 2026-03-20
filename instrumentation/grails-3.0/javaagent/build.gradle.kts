plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.grails")
    module.set("grails-web-url-mappings")
    versions.set("[3.0,)")
    assertInverse.set(true)
  }
}

val grailsVersion = "3.0.6" // first version that the tests pass on
val springBootVersion = "1.2.5.RELEASE"

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.grails:grails-plugin-url-mappings:$grailsVersion")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-3.1:javaagent"))

  testLibrary("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  testLibrary("org.springframework.boot:spring-boot-starter-tomcat:$springBootVersion")

  latestDepTestLibrary("org.springframework.boot:spring-boot-autoconfigure:2.+") // related dependency
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-tomcat:2.+") // related dependency
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

if (!latestDepTest) {
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          if (requested.group == "org.codehaus.groovy") {
            useVersion("3.0.25")
          }
        }
      }
    }
  }
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}

spotless {
  groovy {
    target("src/**/*.groovy")
    licenseHeaderFile(
      rootProject.file("buildscripts/spotless.license.java"),
      "(package|import|(?:abstract )?class)"
    )
    endWithNewline()
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", latestDepTest)

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("metadataConfig", "otel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
