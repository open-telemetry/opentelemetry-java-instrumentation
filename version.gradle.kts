val stableVersion = "2.22.0-SNAPSHOT"
val alphaVersion = "2.22.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
