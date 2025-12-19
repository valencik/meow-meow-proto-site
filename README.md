# Typelevel Website

Prototype Typelevel website, built with [Laika](https://typelevel.org/Laika/) and styled with [Bulma CSS](https://bulma.io/).

## Build & Serve

Requirements:
* A vaguely modern version of scala-cli (at least 1.0.0)
* At least Java 11 (some dependencies won't run on Java 8)

```bash
scala-cli run build.scala -- --serve
```

Then open http://localhost:8080. Press Ctrl+C to stop the server.

To use a different port:

```bash
scala-cli run build.scala -- --serve --port 3000
```


## Build Only

```bash
scala-cli run build.scala
```

This generates the site in `target/site/`.

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

