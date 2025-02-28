val stableVersion = "2.13.3"
val alphaVersion = "2.13.3-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
