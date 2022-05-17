val stableVersion = "1.15.0-SNAPSHOT"
val alphaVersion = "1.15.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
