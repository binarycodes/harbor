# Development environment

Postgres for local development. Harbor keeps the whole library here — bookmarks,
notes, highlights, and the archived PDFs — so nothing runs without it.

```bash
./run.sh db up
```

Then start the app as usual; `application.properties` defaults to exactly this
container, so no configuration is needed.

```bash
./run.sh run
```

`./run.sh db down` stops it and keeps the data. `./run.sh db reset` throws the
volume away, which is how you get back to a first-run empty library.

Requires a container runtime with the Compose plugin (`docker compose` or the
standalone `docker-compose`). The test suite needs the same runtime for a
different reason: Testcontainers starts its own throwaway Postgres, and does not
use this file.

**The credentials are `harbor`/`harbor` and the port is published.** That is fine
on a laptop and nowhere else.
