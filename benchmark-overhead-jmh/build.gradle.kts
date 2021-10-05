import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.champeau.jmh.JMHTask
import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("otel.java-conventions")
  id("otel.jmh-conventions")
}

dependencies {
  jmhImplementation("org.springframework.boot:spring-boot-starter-web:2.5.2")
}

tasks {

  // TODO(trask) without disabling errorprone, jmh task fails with
  //  Task :testing-overhead-jmh:jmhCompileGeneratedClasses FAILED
  //   error: plug-in not found: ErrorProne
  withType<JavaCompile>().configureEach {
    options.errorprone {
      isEnabled.set(false)
    }
  }

  // TODO(trask) move to otel.jmh-conventions?
  val jmhFork = gradle.startParameter.projectProperties["jmh.fork"]?.toInt()
  val jmhWarmupIterations = gradle.startParameter.projectProperties["jmh.warmupIterations"]?.toInt()
  val jmhIterations = gradle.startParameter.projectProperties["jmh.iterations"]?.toInt()
  val jmhIncludes = gradle.startParameter.projectProperties["jmh.includes"]

  // note: if you want to capture a flight recording for a single benchmark, try
  //  -Pjmh.fork=1
  //  -Pjmh.warmupIterations=5
  //  -Pjmh.iterations=5
  //  -Pjmh.includes=<benchmark>
  //  -Pjmh.startFlightRecording=settings=profile.jfc,delay=50s,duration=50s,filename=output.jfr
  // since each iteration is 10 seconds, the flight recording produced will approximately cover the
  // (post-warmup) benchmarking iterations
  val jmhStartFlightRecording = gradle.startParameter.projectProperties.get("jmh.startFlightRecording")

  named<JMHTask>("jmh") {
    val shadowTask = project(":javaagent").tasks.named<ShadowJar>("shadowJar").get()
    inputs.files(layout.files(shadowTask))

    // note: without an exporter, toSpanData() won't even be called
    // (which is good for benchmarking the instrumentation itself)
    val args = mutableListOf(
      "-javaagent:${shadowTask.archiveFile.get()}",
      "-Dotel.traces.exporter=none",
      "-Dotel.metrics.exporter=none",
      // avoid instrumenting HttpURLConnection for now since it is used to make the requests
      // and this benchmark is focused on servlet overhead for now
      "-Dotel.instrumentation.http-url-connection.enabled=false"
    )
    if (jmhStartFlightRecording != null) {
      args.addAll(
        listOf(
          "-XX:+FlightRecorder",
          "-XX:StartFlightRecording=$jmhStartFlightRecording",
          // enabling profiling at non-safepoints helps with micro-profiling
          "-XX:+UnlockDiagnosticVMOptions",
          "-XX:+DebugNonSafepoints"
        )
      )
    }
    // see https://github.com/melix/jmh-gradle-plugin/issues/200
    jvmArgsPrepend.add(args.joinToString(" "))

    if (jmhFork != null) {
      fork.set(jmhFork)
    }
    if (jmhWarmupIterations != null) {
      warmupIterations.set(jmhWarmupIterations)
    }
    if (jmhIterations != null) {
      iterations.set(jmhIterations)
    }
    if (jmhIncludes != null) {
      includes.addAll(jmhIncludes.split(','))
    }

    // TODO(trask) is this ok? if it's ok, move to otel.jmh-conventions?
    outputs.upToDateWhen { false }
  }
}
