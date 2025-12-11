# Typelevel Website

Prototype Typelevel website, built with [Laika](https://typelevel.org/Laika/) and styled with [Bulma CSS](https://bulma.io/).

## Prerequisites

- [Scala CLI](https://scala-cli.virtuslab.org/)

## Build

```bash
scala-cli run build.scala
```

This generates the site in `target/site/`.

## Preview

After building, serve the site locally:

```bash
cd target/site
python3 -m http.server 8080
```

Then open http://localhost:8080

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

