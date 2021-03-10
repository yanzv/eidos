val warn = false

update / evictionWarningOptions := EvictionWarningOptions.default
    .withWarnTransitiveEvictions(warn)
    .withWarnDirectEvictions(warn)

// Given groupID % artifactID % revision % configuration, alphabetize by artifactID, groupID please.
addSbtPlugin("com.eed3si9n"             % "sbt-assembly"         % "0.14.9")
addSbtPlugin("com.eed3si9n"             % "sbt-buildinfo"        % "0.7.0")
addSbtPlugin("net.virtual-void"         % "sbt-dependency-graph" % "0.9.2")
addSbtPlugin("com.typesafe.sbt"         % "sbt-ghpages"          % "0.6.2")
addSbtPlugin("com.typesafe.sbt"         % "sbt-git"              % "1.0.0")
addSbtPlugin("com.typesafe.play"        % "sbt-plugin"           % "2.6.7")
addSbtPlugin("com.jsuereth"             % "sbt-pgp"              % "1.1.0")
addSbtPlugin("com.github.gseitz"        % "sbt-release"          % "1.0.8")
addSbtPlugin("com.typesafe.sbt"         % "sbt-site"             % "1.3.2")
addSbtPlugin("org.xerial.sbt"           % "sbt-sonatype"         % "2.3")
addSbtPlugin("com.typesafe.sbteclipse"  % "sbteclipse-plugin"    % "5.2.4")
