import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import org.sbtidea.SbtIdeaPlugin._

organization  := "org.copygrinder"

version       := "0.1"

scalaVersion  := "2.11.2"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  Resolver.file("local-repo", file("project/lib/")) (Resolver.ivyStylePatterns),
  "spray repo" at "http://repo.spray.io/",
  "JGit repo" at "https://repo.eclipse.org/content/groups/releases/",
  "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

/* SCALA LIBS */
libraryDependencies ++= Seq(
  "io.spray"                  %%  "spray-can"       %  "1.3.1",
  "io.spray"                  %%  "spray-routing"   %  "1.3.1",
  "io.spray"                  %%  "spray-testkit"   %  "1.3.1",
  "org.json4s"                %%  "json4s-jackson"  %  "3.2.10",
  "com.typesafe.akka"         %%  "akka-slf4j"      %  "2.3.4",
  "com.softwaremill.macwire"  %%  "macros"          %  "0.7"
)

/* JAVA LIBS */
libraryDependencies ++= Seq(
  "org.eclipse.jgit"    %   "org.eclipse.jgit"  % "3.4.1.201406201815-r",
  "commons-io"          %   "commons-io"        % "2.4",
  "ch.qos.logback"      %   "logback-classic"   % "1.1.2"
)

/* TEST LIBS */
libraryDependencies ++= Seq(
  "org.scalatest"       %%   "scalatest"                   % "2.2.0"   % "test",
  "org.scalamock"       %%  "scalamock-scalatest-support"  % "3.1.2"   % "test"
)

Revolver.settings.settings

instrumentSettings

ScoverageKeys.highlighting := true

org.scalastyle.sbt.ScalastylePlugin.Settings

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil

EclipseKeys.withSource := true

incOptions := incOptions.value.withNameHashing(true)

ideaExcludeFolders += ".idea"

ideaExcludeFolders += ".idea_modules"

fork := true

addCommandAlias("check", ";scalastyle;scoverage:test")

proguardSettings

ProguardKeys.options in Proguard ++= Seq("-ignorewarnings", "-dontobfuscate", "-dontoptimize")

ProguardKeys.options in Proguard += ProguardOptions.keepMain("org.copygrinder.unpure.system.Boot")

ProguardKeys.merge in Proguard := true

ProguardKeys.mergeStrategies in Proguard += ProguardMerge.discard("META-INF/.*".r)

ProguardKeys.mergeStrategies in Proguard += ProguardMerge.discard("rootdoc.txt")

ProguardKeys.mergeStrategies in Proguard += ProguardMerge.append("reference.conf")

ProguardKeys.proguardVersion in Proguard := "5.0"

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xms1024m", "-Xmx1024m")

net.virtualvoid.sbt.graph.Plugin.graphSettings

ProguardKeys.options in Proguard ++= Seq(
  """
    | -dontwarn scala.**
    | -dontwarn groovy.**
    | -dontwarn org.codehaus.groovy.**
    | -dontwarn javax.**
    | -dontwarn org.specs2.**
    | -dontwarn org.apache.log.**
    | -dontwarn org.apache.log4j.**
    | -dontwarn org.apache.avalon.**
    | -dontwarn spray.json.**
    | -dontwarn spray.caching.**
    | -dontwarn play.**
    | -dontwarn twirl.**
    | -dontwarn org.scalatest.**
    | -dontwarn org.codehaus.janino.**
    | -dontwarn net.liftweb.**
    | -dontwarn com.jcraft.jzlib.**
    |
    |
    | -keep public class scala.Option
    | -keep public class scala.Function0
    | -keep public class scala.Function1
    | -keep public class scala.Function2
    | -keep public class scala.Product
    | -keep public class scala.Tuple2*
    |
    | -keepclassmembers class ** {
    |   ** MODULE$;
    | }
    |
    | -keep class akka.** {
    |  *;
    | }
    |
    | -keep class spray.can.Http** {
    |   *;
    | }
    |
    | -keepattributes *Annotation*
    | -dontskipnonpubliclibraryclassmembers
    |
    |
  """.stripMargin)

  ProguardKeys.options in Proguard ++= Seq(
   """
    |
    | -keep class akka.actor.LightArrayRevolverScheduler { *; }
    | -keep class akka.actor.LocalActorRefProvider { *; }
    | -keep class akka.actor.CreatorFunctionConsumer { *; }
    | -keep class akka.actor.TypedCreatorFunctionConsumer { *; }
    | -keep class akka.dispatch.BoundedDequeBasedMessageQueueSemantics { *; }
    | -keep class akka.dispatch.UnboundedMessageQueueSemantics { *; }
    | -keep class akka.dispatch.UnboundedDequeBasedMessageQueueSemantics { *; }
    | -keep class akka.dispatch.DequeBasedMessageQueueSemantics { *; }
    | -keep class akka.actor.LocalActorRefProvider$Guardian { *; }
    | -keep class akka.actor.LocalActorRefProvider$SystemGuardian { *; }
    | -keep class akka.dispatch.UnboundedMailbox { *; }
    | -keep class akka.actor.DefaultSupervisorStrategy { *; }
    | -keep class akka.event.slf4j.Slf4jLogger { *; }
    | -keep class akka.event.Logging$LogExt { *; }
    | -keep class akka.dispatch.MultipleConsumerSemantics { *; }
    | -keep class akka.dispatch.BoundedControlAwareMessageQueueSemantics { *; }
    | -keep class akka.dispatch.UnboundedControlAwareMessageQueueSemantics { *; }
    | -keep class akka.event.EventStreamUnsubscriber { *; }
    |
    | -keep class spray.can.HttpExt { *; }
    | -keep class spray.**
    | -keep class akka.**
    |
    | -keep class org.parboiled.scala.WithContextAction*
    | -keep class ch.qos.logback.core.FileAppender
    |
    | -keepattributes *Annotation*
    | -keepattributes Signature
    | -keepattributes InnerClasses
    | -keepattributes InnerClasses,EnclosingMethod
    | -dontskipnonpubliclibraryclasses
    | -dontskipnonpubliclibraryclassmembers
    |
    |-keep class akka.** {
    |  public <methods>;
    |}
    |
    |-keep class com.typesafe.config.Config
    |-keep class scala.Function0
    |-keep class scala.Function2
    |-keep class scala.Function3
    |-keep class scala.Option
    |-keep class scala.PartialFunction
    |-keep class scala.Predef$$less$colon$less
    |-keep class scala.Tuple2
    |-keep class scala.Tuple3
    |-keep class scala.collection.Iterable
    |-keep class scala.collection.GenIterable
    |-keep class scala.collection.GenSeq
    |-keep class scala.collection.LinearSeq
    |-keep class scala.collection.Seq
    |-keep class scala.collection.Traversable
    |-keep class scala.collection.TraversableOnce
    |-keep class scala.collection.TraversableLike
    |-keep class scala.collection.Iterator
    |-keep class scala.collection.SeqLike {
    |    # for SI-5397
    |    public protected *;
    |}
    |-keep class scala.collection.generic.CanBuildFrom
    |-keep class scala.collection.immutable.TreeMap
    |-keep class scala.collection.immutable.Map
    |-keep class scala.collection.immutable.SortedMap
    |-keep class scala.collection.immutable.MapLike
    |-keep class scala.collection.immutable.Seq
    |-keep class scala.collection.immutable.TreeSet
    |-keep class scala.collection.immutable.Set
    |-keep class scala.collection.immutable.Iterable
    |-keep class scala.collection.immutable.IndexedSeq
    |-keep class scala.collection.immutable.List
    |-keep class scala.collection.immutable.Queue
    |-keep class scala.collection.immutable.Traversable
    |-keep class scala.collection.immutable.Vector
    |-keep class scala.collection.mutable.Map
    |-keep class scala.collection.mutable.Builder
    |-keep class scala.collection.mutable.Buffer
    |-keep class scala.collection.mutable.ArrayBuffer
    |-keep class scala.collection.mutable.WrappedArray
    |-keep class scala.collection.mutable.Queue
    |-keep class scala.collection.mutable.Set
    |-keep class scala.collection.mutable.StringBuilder
    |-keep class scala.concurrent.BlockContext
    |-keep class scala.concurrent.CanAwait
    |-keep class scala.concurrent.ExecutionContext
    |-keep class scala.concurrent.Future
    |-keep class scala.concurrent.Promise
    |-keep class scala.concurrent.forkjoin.ForkJoinPool$ForkJoinWorkerThreadFactory
    |-keep class scala.concurrent.forkjoin.ForkJoinPool
    |-keep class scala.concurrent.forkjoin.ForkJoinTask
    |-keep class scala.concurrent.forkjoin.ForkJoinPool$ManagedBlocker
    |-keep class scala.collection.GenTraversableOnce
    |-keep class scala.concurrent.duration.FiniteDuration
    |-keep class scala.concurrent.duration.Duration
    |-keep class scala.concurrent.duration.Deadline
    |-keep class scala.math.Integral
    |-keep class scala.math.Numeric
    |-keep class scala.math.Ordering
    |-keep class scala.reflect.ClassTag
    |-keep class scala.runtime.IntRef
    |-keep class scala.runtime.BoxedUnit
    |-keep class scala.runtime.IntRef
    |-keep class scala.runtime.ObjectRef
    |-keep class scala.runtime.ByteRef
    |-keep class scala.runtime.CharRef
    |-keep class scala.runtime.DoubleRef
    |-keep class scala.runtime.FloatRef
    |-keep class scala.runtime.ShortRef
    |-keep class scala.runtime.LongRef
    |-keep class scala.runtime.BooleanRef
    |-keep class scala.util.Try
    |
  """.stripMargin.head.toString)
