# RestExpress Product Roadmap

This roadmap describes the proposed modernization of RestExpress and its related ecosystem. It is intentionally organized into small, independently deliverable work items so that items can be discussed, implemented, and completed one at a time.

The roadmap is directional rather than a commitment to dates. Priorities may change based on user feedback, compatibility requirements, security findings, and contributor availability.

## Product direction

RestExpress should remain a small, explicit, Netty-native framework for high-performance Java APIs. It should not attempt to duplicate the full feature set of application platforms such as Spring Boot or Quarkus.

Its primary differentiators should be:

- Explicit, centralized route declarations.
- A small core with optional, well-defined modules.
- Low runtime overhead and direct Netty integration.
- Plain Java controllers and minimal framework ceremony.
- A shared operation model that can expose capabilities through REST, OpenAPI, and MCP.
- Secure, observable production behavior by default.

## Guiding principles

1. **Stability before breadth.** Restore a supported build and dependency baseline before adding major capabilities.
2. **Keep the core small.** OpenAPI, MCP, persistence, security, and observability should use stable extension points rather than expanding the core unnecessarily.
3. **Explicit over implicit.** Routes and agent tools should be easy to discover and audit. Dangerous capabilities must never be exposed automatically.
4. **Compatibility is a feature.** Releases should document supported JDKs, protocol versions, migration steps, and deprecations.
5. **Secure by default.** Authentication, authorization, validation, limits, and auditability must be considered in initial API designs.
6. **Measure regressions.** Correctness, compatibility, and performance should be continuously tested.

---

## Phase 1 — Restore confidence and usability

**Target:** `0.12`

### R1. Modernize the runtime

- [ ] Select and document the minimum supported JDK, initially Java 17, with Java 21 included in CI.
- [ ] Upgrade Netty, Jackson, Maven plugins, JUnit, and other security-sensitive dependencies.
- [ ] Remove obsolete Java 6/7 assumptions and replace deprecated APIs.
- [ ] Document supported JDK, Maven, and dependency versions in a compatibility matrix.
- [ ] Establish a repeatable process for dependency and JDK upgrades.

**Completion criteria:** Core modules build and pass tests on all supported JDKs without undocumented workarounds.

### R2. Unify releases and builds

- [ ] Decide whether the ecosystem will use one multi-module build or a coordinated release train.
- [ ] Align compatible versions across RestExpress, PluginExpress, HyperExpress, RepoExpress, DateAdapterJ, Syntaxe, scaffolds, archetypes, and examples.
- [ ] Build the complete supported ecosystem in CI.
- [ ] Add dependency vulnerability scanning and automated dependency-update proposals.
- [ ] Produce reproducible artifacts and publish supported releases to Maven Central.
- [ ] Introduce semantic versioning and a formal deprecation policy.

**Completion criteria:** A tagged release can be built, tested, and published from a clean checkout through an automated process.

### R3. Refresh documentation

- [ ] Replace outdated Gson, XStream, Ant, Java 6/7, and historical scaffold instructions.
- [ ] Publish a current “build your first API” guide.
- [ ] Document routing, serialization, configuration, exception handling, plugins, and deployment.
- [ ] Generate Java API documentation.
- [ ] Publish runnable examples for supported configurations.
- [ ] Clearly label deprecated and historical modules or documents.

**Completion criteria:** A new user can create, run, test, and package an API using only current documentation.

### R4. Strengthen testing

- [ ] Migrate tests to JUnit 5.
- [ ] Add HTTP integration tests against a real RestExpress server.
- [ ] Expand route matching, serialization, exception mapping, malformed input, concurrency, and shutdown coverage.
- [ ] Add compatibility tests for supported JDKs and modules.
- [ ] Add fuzz or property-based tests for routing and request parsing where useful.
- [ ] Establish performance-regression benchmarks and publish baseline results.

**Completion criteria:** CI provides reliable correctness, compatibility, security, and performance signals.

---

## Phase 2 — Modern developer experience and machine-readable APIs

**Target:** `0.13`

### R5. Introduce a shared operation metadata model

Create a framework-neutral model used by routes, documentation, policy, OpenAPI, and MCP. It should represent at least:

- Operation name, description, tags, and version.
- Input and output Java types and JSON Schemas.
- Path, query, header, and body parameters.
- Authentication requirements and authorization scopes.
- Read-only, idempotent, and destructive-operation hints.
- Timeouts, limits, and relevant observability labels.

- [ ] Design the public operation metadata API.
- [ ] Integrate metadata with the route DSL.
- [ ] Validate duplicate names, routes, and incompatible declarations at startup.
- [ ] Define stable extension points for metadata consumers.

**Completion criteria:** OpenAPI and MCP can consume the same metadata without independently inferring controller behavior.

### R6. Deliver first-class OpenAPI 3.1 support

- [ ] Generate OpenAPI 3.1 documents from route and operation metadata.
- [ ] Generate JSON Schema for request and response types.
- [ ] Represent authentication, authorization scopes, errors, pagination, and common headers.
- [ ] Provide optional Swagger UI hosting.
- [ ] Support build-time export and runtime document endpoints.
- [ ] Optionally validate requests and responses against declared schemas.
- [ ] Provide migration guidance from the existing Swagger plugin.

**Completion criteria:** A representative service produces a valid OpenAPI 3.1 document and can be exercised through the optional UI.

### R7. Add experimental first-class MCP server support

MCP support should be a separately versioned, supported module built on the shared operation model—not a collection of unrelated controller endpoints.

#### Core capabilities

- [ ] Implement the currently selected MCP specification version and publish the support policy.
- [ ] Support stateless remote HTTP transport.
- [ ] Provide a `stdio` adapter for local IDE, CLI, and desktop-agent use.
- [ ] Implement tools with JSON Schema inputs and structured outputs.
- [ ] Implement resources and resource templates.
- [ ] Implement prompts.
- [ ] Support capability discovery and protocol-version negotiation.
- [ ] Map Java exceptions to useful, non-sensitive MCP errors.
- [ ] Provide extension points for MCP specification extensions.
- [ ] Add protocol conformance and interoperability tests.

#### API design

Support explicit MCP-native declarations as well as opt-in projection of REST operations:

```java
server.uri("/orders/{orderId}", orderController)
    .name("getOrder")
    .description("Retrieve an order")
    .requestType(GetOrderRequest.class)
    .responseType(Order.class)
    .exposeToMcp(mcp -> mcp
        .toolName("orders_get")
        .readOnly()
        .idempotent());
```

MCP-only operations should not require an artificial REST route:

```java
mcp.tool("orders_cancel", cancelOrderHandler)
    .description("Cancel an order that has not shipped")
    .input(CancelOrderRequest.class)
    .output(Order.class)
    .destructive()
    .requiresScope("orders:write");
```

#### Exposure policy

- [ ] Make MCP exposure deny-by-default.
- [ ] Support explicit MCP-native tools as the recommended mode.
- [ ] Support opt-in projection of individual REST operations.
- [ ] Optionally import OpenAPI operations as draft tool definitions requiring human review.
- [ ] Never expose all REST routes automatically in production defaults.

#### Security requirements in the initial design

- [ ] Define per-tool authentication and authorization metadata.
- [ ] Validate all tool inputs and structured outputs.
- [ ] Enforce bounded request, response, and tool-output sizes.
- [ ] Provide sensitive-field redaction.
- [ ] Account for SSRF, filesystem access, and untrusted external content.
- [ ] Model approval requirements for destructive operations.
- [ ] Record auditable invocation events without leaking secrets.
- [ ] Distinguish the calling client identity from the end-user identity.

**Completion criteria:** A sample service can deliberately expose selected operations as MCP tools over HTTP and `stdio`, pass conformance tests, and leave undeclared routes inaccessible through MCP.

### R8. Redesign project generation

Replace fragmented scaffolds and archetypes with one supported project generator or CLI.

- [ ] Generate minimal REST, OpenAPI, and MCP-capable applications.
- [ ] Offer optional PostgreSQL, MongoDB, Cassandra, and Redis integrations according to support status.
- [ ] Generate tests, configuration, Dockerfiles, health checks, and CI configuration.
- [ ] Support non-interactive invocation for automation.
- [ ] Keep generated applications small and understandable.

Example:

```shell
restexpress new orders-api --database postgres --openapi --mcp --docker
```

**Completion criteria:** A generated project builds, tests, packages, and runs without manual repair.

### R9. Improve the route DSL

- [ ] Add typed path, query, header, and body parameters.
- [ ] Add declarative request and response types.
- [ ] Add route groups and shared path prefixes.
- [ ] Add a clear API-versioning mechanism.
- [ ] Add startup validation for ambiguous and duplicate routes.
- [ ] Add asynchronous handlers based on `CompletionStage`.
- [ ] Evaluate virtual-thread execution without making it mandatory.

**Completion criteria:** Common API contracts are type-safe and supply enough metadata for validation, OpenAPI, and MCP.

### R10. Overhaul configuration

- [ ] Add typed configuration with startup validation.
- [ ] Support files, environment variables, command-line arguments, and secret references.
- [ ] Define and document deterministic source precedence.
- [ ] Permit reload only for explicitly reloadable settings.
- [ ] Prevent accidental secret disclosure in diagnostics and logs.

**Completion criteria:** Invalid configuration fails early with actionable messages and production overrides are predictable.

---

## Phase 3 — Production readiness

**Targets:** `0.14` and `0.15`

### R11. Observability by default

- [ ] Integrate Micrometer metrics through an optional module.
- [ ] Integrate OpenTelemetry traces and context propagation.
- [ ] Support structured JSON logging.
- [ ] Support W3C trace context and correlation IDs.
- [ ] Emit standard request count, latency, error, status, and route metrics.
- [ ] Add MCP method and tool metrics without recording sensitive arguments.
- [ ] Document cardinality controls and safe defaults.

**Completion criteria:** REST and MCP requests can be traced and monitored end to end using standard tooling.

### R12. Operational endpoints and lifecycle

- [ ] Add liveness, readiness, and startup probes.
- [ ] Expose build and version information safely.
- [ ] Add extensible dependency health indicators.
- [ ] Implement graceful shutdown with request draining and bounded deadlines.
- [ ] Add optional protected diagnostics for routes, tools, and configuration metadata.

**Completion criteria:** Services behave predictably under orchestration, rolling deployment, and shutdown.

### R13. Security baseline

- [ ] Add current OAuth/OIDC and JWT support.
- [ ] Add secure CORS defaults.
- [ ] Enforce request-body, header, upload, timeout, and concurrency limits.
- [ ] Add rate-limiting extension points and a supported implementation.
- [ ] Add trusted-proxy configuration.
- [ ] Add standard security response headers.
- [ ] Redact credentials and sensitive fields from errors, logs, traces, and audits.
- [ ] Publish a vulnerability-reporting and supported-version policy.

**Completion criteria:** A generated production service passes the documented security checklist without requiring custom framework patches.

### R14. Production MCP security and governance

- [ ] Integrate OAuth/OIDC with MCP and support per-tool scopes.
- [ ] Propagate principals safely into tool handlers.
- [ ] Enforce tool-specific rate, timeout, output, and concurrency limits.
- [ ] Implement approval policies for destructive or sensitive operations.
- [ ] Add cancellation and deadlines.
- [ ] Add audit records for every invocation and approval decision.
- [ ] Support long-running tasks according to the selected MCP specification.
- [ ] Support multi-round-trip input and confirmation flows where applicable.
- [ ] Support cache metadata for tool, prompt, and resource listings.
- [ ] Test compatibility against selected MCP clients and publish results.

**Completion criteria:** MCP can be deployed remotely with documented identity, authorization, limits, observability, and audit controls.

### R15. Resilience and overload protection

- [ ] Add route- and tool-level timeouts.
- [ ] Add bulkheads and concurrency limits.
- [ ] Integrate or provide circuit breakers.
- [ ] Restrict automatic retries to explicitly safe operations.
- [ ] Add backpressure and deterministic overload responses.
- [ ] Add idempotency-key support for creation and command endpoints.

**Completion criteria:** Services degrade predictably under dependency failure, overload, and cancellation.

---

## Phase 4 — Data and advanced API capabilities

**Target:** post-`0.15`, with selected capabilities considered for `1.0`

### R16. Relational persistence

- [ ] Provide a supported PostgreSQL path using JDBC, Jdbi, or jOOQ.
- [ ] Provide transaction-boundary guidance and integration.
- [ ] Supply pagination, filtering, and ordering helpers where they remain datastore-neutral.
- [ ] Include schema-migration templates.
- [ ] Avoid creating a proprietary ORM.

**Completion criteria:** The generator can produce a tested relational service with transactions and migrations.

### R17. Modern serialization and content handling

- [ ] Treat JSON as the primary representation.
- [ ] Standardize REST errors using RFC 9457 Problem Details.
- [ ] Support streaming responses with cancellation and backpressure.
- [ ] Improve multipart and large-upload handling.
- [ ] Keep XML in an optional compatibility module only if sustained demand exists.

**Completion criteria:** JSON APIs have consistent contracts and large or streaming payloads do not require unsafe buffering.

### R18. Hypermedia consolidation

- [ ] Decide whether HAL and Siren remain strategic or enter maintenance mode.
- [ ] If retained, update HAL and Siren support.
- [ ] Add typed link builders and pagination links.
- [ ] Integrate link metadata with OpenAPI and shared operation metadata where appropriate.

**Completion criteria:** Hypermedia support has a clear support status and no ambiguous overlap with core concerns.

### R19. Real-time and long-running interactions

- [ ] Add Server-Sent Events before general WebSocket support.
- [ ] Add WebSockets only in response to demonstrated user demand.
- [ ] Document threading, ordering, cancellation, and backpressure behavior.
- [ ] Integrate long-running operations with MCP tasks where useful.

**Completion criteria:** Supported real-time mechanisms have explicit resource and lifecycle guarantees.

---

## Architectural cleanup supporting all phases

These items should be scheduled with the roadmap work that first requires them:

- [ ] Define stable public SPIs for serialization, exception mapping, plugins, pipeline processors, metadata, and transports.
- [ ] Standardize package naming and publish a migration strategy for `org.restexpress` and `com.strategicgains` packages.
- [ ] Replace repeated reflective controller lookup with cached method handles or generated bindings where benchmarks justify it.
- [ ] Separate supported public APIs from internal implementation packages.
- [ ] Add compatibility checks for public API and wire-format changes.
- [ ] Publish migration guides for every breaking release.

## Proposed release sequence

| Release | Primary outcome |
| --- | --- |
| `0.12` | Java 17/21 build baseline, upgraded dependencies, unified CI, current documentation, stronger tests |
| `0.13` | Shared operation metadata, OpenAPI 3.1, optional Swagger UI, project CLI, typed configuration, experimental MCP |
| `0.14` | OpenTelemetry, Micrometer, health endpoints, graceful draining, initial production MCP integration |
| `0.15` | OIDC/JWT, limits, resilience, stable asynchronous handlers, stable and governed MCP server API |
| `1.0` | Stable public API/SPI, Java 21 baseline, compatibility guarantees, complete migration guidance |

## Explicit non-priorities

The following should not take precedence over runtime modernization, compatibility, OpenAPI, MCP safety, and production readiness:

- Building a dependency-injection container.
- Building a proprietary ORM.
- Native-image support before Java 17/21 support is solid.
- Adding more databases before existing persistence modules have clear support levels and tests.
- GraphQL without demonstrated user demand.
- Maintaining every historical serialization format indefinitely.
- Automatically publishing all REST routes as agent tools.

## How to work this roadmap

1. Select one unchecked work item and open an issue describing its scope.
2. Record API decisions and compatibility consequences before implementation.
3. Add tests, documentation, and migration notes in the same change as the feature.
4. Check the item only after its completion criteria are met on supported platforms.
5. Update release targets when priorities or protocol requirements change.

Contributions and discussion are welcome. Please keep proposals aligned with the product direction and guiding principles above.
