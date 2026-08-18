plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

dependencies {
  implementation(gradleApi())
  implementation(localGroovy())

  // dependencySubstitution is applied to this dependency (see seetings.gradle.kts)
  implementation("io.opentelemetry.instrumentation:gradle-plugins")

  implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
  implementation("org.eclipse.aether:aether-transport-http:1.1.0")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.0")
  implementation("com.google.guava:guava:33.7.1-jre")
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1") {
    // plexus-xml 4.1+ pulls in Maven 4 API which uses JPMS-only service registration,
    // causing "No XmlService implementation found" in Gradle's classloader
    // We exclude plexus-xml and plexus-utils here because our current usages of the shadow plugin
    // don't require it, the  failure happens in spdx-gradle-plugin that can continue using and
    // older version of plexus-xml and plexus-utils
    exclude("org.codehaus.plexus", "plexus-utils")
    exclude("org.codehaus.plexus", "plexus-xml")
  }
  implementation("org.apache.httpcomponents:httpclient:4.5.14")
  implementation("com.gradle.develocity:com.gradle.develocity.gradle.plugin:4.5.0")
  implementation("org.sonatype.gradle.plugins:scan-gradle-plugin:4.0.0")
  implementation("ru.vyarus:gradle-animalsniffer-plugin:2.0.1")
  implementation("org.spdx:spdx-gradle-plugin:0.12.0")
  // When updating, also update dependencyManagement/build.gradle.kts
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.18.12")
  // Generates OSGi bundle metadata for published library artifacts (see otel.osgi-conventions)
  implementation("biz.aQute.bnd:biz.aQute.bnd.gradle:7.3.0")
  implementation("gradle.plugin.io.morethan.jmhreport:gradle-jmh-report:0.9.6")
  implementation("me.champeau.jmh:jmh-gradle-plugin:0.7.3")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
  implementation("net.ltgt.gradle:gradle-nullaway-plugin:3.1.0")
  implementation("me.champeau.gradle:japicmp-gradle-plugin:0.4.6")
  // Used by otel.spring-native-test-conventions for typed access to the metadata repository
  // extension. Provided at runtime by smoke-test modules that apply this plugin via settings.
  compileOnly("org.graalvm.buildtools:native-gradle-plugin:1.1.8")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.14.4"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.assertj:assertj-core:3.27.7")
}
