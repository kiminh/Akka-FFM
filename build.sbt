name := "Akka-FFM"

version := "1.0"

scalaVersion := "2.11.8"

scalaVersion := "2.11.8"

resolvers += "mvn2" at "http://repo1.maven.org/maven2/"

// https://mvnrepository.com/artifact/com.googlecode.netlib-java/netlib-java
resolvers += "mvnrepository" at "https://mvnrepository.com/artifact/com.googlecode.netlib-java"

libraryDependencies += "com.googlecode.netlib-java" % "netlib-java" % "1.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Akka Repository" at "http://repo.akka.io/releases/"

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % "2.4.9"

libraryDependencies += "com.typesafe.akka" % "akka-remote_2.11" % "2.4.9"

libraryDependencies += "com.typesafe.akka" % "akka-cluster_2.11" % "2.4.9"

libraryDependencies += "com.typesafe.akka" % "akka-stream_2.11" % "2.4.9"

libraryDependencies += "com.typesafe.akka" % "akka-contrib_2.11" % "2.4.9"

libraryDependencies += "org.scalanlp" %% "breeze" % "0.12"

libraryDependencies +=  "org.scalanlp" %% "breeze-natives" % "0.12"
