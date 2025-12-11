# Typelevel Website

Prototype Typelevel website, built with [Laika](https://typelevel.org/Laika/) and styled with [Bulma CSS](https://bulma.io/).

## Prerequisites

- [Scala CLI](https://scala-cli.virtuslab.org/)

## Build

```bash
scala-cli run build.scala
```

This generates the site in `target/site/`.

## Build & Serve

To build and immediately serve the site locally with http4s:

```bash
scala-cli run build.scala -- --serve
```

Then open http://localhost:8080. Press Ctrl+C to stop the server.

To use a different port:

```bash
scala-cli run build.scala -- --serve --port 3000
```

## Structure

```
src/
├── default.template.html   # Main HTML template
├── css/main.css            # Custom styles
├── templates/              # Partial templates (nav, footer)
├── README.md               # Landing page
├── learn/                  # Learning resources
├── blog/                   # Blog posts
├── foundation/             # About Typelevel
└── community/              # Community info & Code of Conduct
```

