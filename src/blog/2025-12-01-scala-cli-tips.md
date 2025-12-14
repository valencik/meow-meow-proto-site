# Scala CLI Tips and Tricks

*December 1, 2025*

Discover productivity tips for getting the most out of Scala CLI in your daily development workflow.

## Quick Scripts

Scala CLI makes it trivial to run one-off scripts:

```scala
//> using scala 3.3
//> using dep org.typelevel::cats-core:2.12.0

import cats.syntax.all._

@main def hello = 
  println("Hello" |+| " " |+| "Typelevel!")
```

## Watch Mode

Use `--watch` to automatically recompile on file changes:

```bash
scala-cli run . --watch
```

## Packaging

Create standalone executables with:

```bash
scala-cli package . -o myapp --assembly
```

Perfect for distributing your applications without requiring Scala to be installed.

