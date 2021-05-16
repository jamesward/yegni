YEGNI
-----

Run the Flaky Server:
```
export GOOGLE_CLOUD_PROJECT=YOUR_GCP_PROJECT
export GOOGLE_APPLICATION_CREDENTIALS=YOUR_SERVICE_ACCOUNT_JSON
./sbt "runMain Flaky"
```

Run the WebApp:
```
export GOOGLE_CLOUD_PROJECT=YOUR_GCP_PROJECT
export GOOGLE_APPLICATION_CREDENTIALS=YOUR_SERVICE_ACCOUNT_JSON
./sbt ~reStart
```

Run Flaky with GraalVM Agent (to generate configs)
```
./sbt flakyGraalRun
```

Create a Flaky native image with GraalVM
```
./sbt flakyGraal
```

```
docker build -t yegni-flaky -f flaky.Dockerfile .
```

```
export GOOGLE_CLOUD_PROJECT=YOUR_GCP_PROJECT
export GOOGLE_APPLICATION_CREDENTIALS=YOUR_SERVICE_ACCOUNT_JSON
docker run -it \
  -p8080:8080 \
  -eGOOGLE_CLOUD_PROJECT=$GOOGLE_CLOUD_PROJECT \
  -eGOOGLE_APPLICATION_CREDENTIALS=/certs/svc_account.json \
  -v$GOOGLE_APPLICATION_CREDENTIALS:/certs/svc_account.json \
  yegni-flaky
```

## TODO

- WebApp GraalVM
- OpenTelemetry w/ GraalVM
- Scala lazy val unsafe GraalVM weirdness (bug?)

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




## How to send trace context

```
curl localhost:8081 -H "x-cloud-trace-context: 105445aa7843bc8bf206b12000100000/0000000000000021;o=1"
```

or

```
curl localhost:8081 -H "traceparent:  00-ff000000000000000000000000000041-ff00000000000041-01"
```