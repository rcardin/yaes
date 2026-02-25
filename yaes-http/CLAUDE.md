## yaes-http

HTTP module for λÆS.

### SBT Project Naming

The HTTP server subproject is defined in `build.sbt` as `lazy val server = project.in(file("yaes-http/server"))`. Use the **project name**, not the directory path:

```bash
# ✅ CORRECT
sbt "server/compile"
sbt "server/test"
sbt "server/testOnly in.rcard.yaes.http.server.HttpParserSpec"

# ❌ INCORRECT
sbt "yaes-http/server/compile"
```
