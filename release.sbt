import ReleaseTransformations._
import org.clulab.sbt.BuildUtils

releaseProcess :=
    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease
    ) ++
    Seq[ReleaseStep](releaseStepCommandAndRemaining(
      if (BuildUtils.useArtifactory) "+publish"
      else "+publishSigned"
    )) ++
    Seq[ReleaseStep](
      setNextVersion,
      commitNextVersion
    ) ++
    (
      if (BuildUtils.useArtifactory) Seq.empty[ReleaseStep]
      else Seq[ReleaseStep](releaseStepCommandAndRemaining("sonatypeReleaseAll"))
    ) ++
    Seq[ReleaseStep](
      pushChanges
    )

