YEGNI
-----

Run the Flaky Server:
```
./sbt "runMain Flaky"
```

Run the WebApp:
```
./sbt ~reStart
```

## TODO

- Flaky GraalVM
- HttpClient ZIO
- WebApp
- WebApp GraalVM
- OpenTelemetry
- OpenTelemetry w/ GraalVM

## Notes

Things can fail
 - microservice can be flaky or sometimes slow

Lets start with something easy
 - stdout

Effect systems making it easy to handle / recover from unexpected errors 
 - stdout

Flaky server
 - Http handlers as effects
 - graalvm ready
   - no magic / reflection

Server-to-service
 - sauerkraut (type class derivation)

Observability

Retry


