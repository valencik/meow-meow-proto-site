# Building Custom http4s Middleware

*December 5, 2025*

Learn how to create reusable middleware components for your http4s applications.

## What is Middleware?

Middleware wraps your HTTP routes to add cross-cutting functionality like logging, authentication, or request timing.

## A Simple Timing Middleware

```scala
import org.http4s._
import cats.effect._
import cats.data.Kleisli

def timing[F[_]: Temporal]: HttpRoutes[F] => HttpRoutes[F] = 
  routes => Kleisli { req =>
    for {
      start <- Clock[F].monotonic
      response <- routes(req)
      end <- Clock[F].monotonic
      _ <- Sync[F].delay(println(s"Request took ${end - start}"))
    } yield response
  }
```

## Composing Middleware

The power of middleware comes from composition — stack multiple middleware layers to build sophisticated request pipelines.

