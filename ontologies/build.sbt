
name := "eidos-ontologies"


libraryDependencies ++= {
  val  procVer = "8.2.3"

  Seq(
    "org.clulab"                 %% "processors-main"          % procVer,
    // local logging
    "ch.qos.logback"              % "logback-classic"  % "1.0.10",
    // dependency logging
    "org.apache.logging.log4j"    % "log4j-core"       % "2.12.0",
    // config
    "com.typesafe"                % "config"           % "1.4.0",

  )
}