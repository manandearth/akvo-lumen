# Introduction

Lumen used Duct to organise.

Lumen serve an API.


TODO: write [great documentation](http://jacobian.org/writing/what-to-write/)


## Concepts

Lumen is a multi tenant system. There is a central component that handles the
different tenants. We call this tenant manager.


------------------------------------------------------------------------

## Developing

- Install Postgres and make sure that CLI tools are available.


### Setup

When you first clone this repository, run:

```sh
lein setup
```

This will create files for local configuration, and prep your system
for the project.

### Environment

To begin developing, start with a REPL.

```sh
lein repl
```
Run `dev` to switch to dev namespace.

```clojure
user=> (dev)
:started
```

Run `go` to initiate and start the system.

```clojure
user=> (go)
:started
```

By default this creates a web server at <http://localhost:3000>.

When you make changes to your source files, use `reset` to reload any
modified files and reset the server.

```clojure
user=> (reset)
:reloading (...)
:resumed
```

### Documentation

Documentation exists in doc/ this is generated from resource/doc. To generate
new documentation:

```sh
lein codox
```

### Testing

Testing is fastest through the REPL, as you avoid environment startup
time.

```clojure
user=> (test)
...
```

But you can also run tests through Leiningen.

```sh
lein test
```

### Migrations

Migrations are handled by [ragtime][]. Migration files are stored in
the `resources/migrations` directory, and are applied in alphanumeric
order.

To update the database to the latest migration, open the REPL and run:

```clojure
user=> (migrate)
Applying 20150815144312-create-users
Applying 20150815145033-create-posts
```

To rollback the last migration, run:

```clojure
user=> (rollback)
Rolling back 20150815145033-create-posts
```

Note that the system needs to be setup with `(init)` or `(go)` before
migrations can be applied.

[ragtime]: https://github.com/weavejester/ragtime


## Deploying

FIXME: steps to deploy
