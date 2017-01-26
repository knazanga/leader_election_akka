lazy val root = (project in file(".")).
  settings(
    name := "project",
    version := "1.0",
    scalaVersion := "2.11.7"
  )

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.0"
libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.12.0-M3"
libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.4.0"

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
   {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
   }
 }

assemblyJarName in assembly := "alto.jar"



