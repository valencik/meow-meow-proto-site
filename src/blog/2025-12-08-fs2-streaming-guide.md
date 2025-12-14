# Getting Started with FS2 Streaming

*December 8, 2025*

A gentle introduction to functional streaming with FS2, the purely functional streaming library for Scala.

## What is FS2?

FS2 is a library for purely functional, effectful, and polymorphic stream processing in Scala. It's built on top of Cats Effect and integrates seamlessly with the Typelevel ecosystem.

## Your First Stream

```scala
import fs2.Stream
import cats.effect.IO

val stream: Stream[IO, Int] = Stream(1, 2, 3)
  .evalMap(n => IO.println(s"Processing: $n").as(n * 2))

stream.compile.toList // IO[List[Int]]
```

## Why Streams?

Streams let you process data incrementally, handling large datasets without loading everything into memory.

