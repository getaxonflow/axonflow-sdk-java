# masfeat_registry_summary (masfeat real-wire fields, #3254 pin-advance batch)

Real-stack leg for the getaxonflow/axonflow-enterprise#3254 pin-advance
batch: the masfeat models now carry the real wire mapping (see
`MASFEATTypes` and the `MASFEATNamespace` parsers).

MAS FEAT is an Enterprise module. On a community build the orchestrator
registers no masfeat routes (`masfeat_community.go` `RegisterRoutes` is a
no-op), so this test asserts one of two legitimate outcomes through the
SDK's real public surface (`client.masfeat().getRegistrySummary()`)
against a real running agent, no mocks:

- **Enterprise leg:** the summary parses; the #3254 real fields
  (`org_id`, `assessments_due`, `kill_switches_triggered`, the
  suffix-less materiality counters) are readable; the deprecated
  `by_use_case`/`by_status` fiction maps are null.
- **Community leg:** the call is refused at the HTTP level (route
  absent) and the SDK surfaces a clean `AxonFlowException` - NOT a
  parse failure. The test prints a NOTE that the enterprise leg was not
  exercised; real-field assertions then rest on the source-derived
  WireMock suite (`MASFEATRealWireTest`).

## Run

```bash
export AXONFLOW_ENDPOINT=http://127.0.0.1:38080   # default
export AXONFLOW_CLIENT_ID=demo-client             # default
export AXONFLOW_CLIENT_SECRET=demo-secret         # default

mvn install -DskipTests
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
SDK_JAR=$(ls target/axonflow-sdk-*.jar | grep -v sources | grep -v javadoc | head -1)
java -cp "$SDK_JAR:$(cat /tmp/cp.txt)" \
  runtime-e2e/masfeat_registry_summary/MasfeatRegistrySummaryTest.java
```
