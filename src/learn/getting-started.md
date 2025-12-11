# Getting Started

Welcome to Typelevel! This guide will help you get started with functional programming in Scala using our ecosystem of libraries.

## Prerequisites

- Scala 2.13 or Scala 3
- sbt or scala-cli

## Your First Project

Add Cats Effect to your build:

```scala
libraryDependencies += "org.typelevel" %% "cats-effect" % "3.6.3"
```

Then write your first program:

```scala
import cats.effect._

object HelloWorld extends IOApp.Simple {
  val run = IO.println("Hello, Typelevel!")
}
```

## Next Steps

- Learn about [Cats Effect](https://typelevel.org/cats-effect/)
- Join our [Discord community](https://discord.gg/XF3CXcMzqD)

