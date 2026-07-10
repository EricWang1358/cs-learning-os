---
title: "HTTP Request Lifecycle"
area: cs-fundamentals
track: networking
order: 50
status: demo
visibility: core
tags: [http, dns, tls, api, backend]
prerequisites: []
related: [database-indexes, project-crud-app]
sources: []
summary: "Trace a browser request from URL resolution through server work to a validated response."
---

# HTTP Request Lifecycle

## Request Path

A browser resolves the host with DNS, establishes a transport connection, completes TLS for HTTPS, then sends an HTTP request containing a method, path, headers, and sometimes a body. A reverse proxy or application router selects code to handle it.

## Server Path

A well-structured endpoint authenticates the caller, validates input, executes business logic, reads or writes storage, maps expected failures to a clear status code, and returns a response. Log a request identifier so the client-side failure can be connected to server-side evidence.

```text
client -> DNS -> TCP/TLS -> proxy/router -> handler -> database
client <- response headers and body <- handler <- database
```

## Status Codes Worth Knowing

- `200` means a successful read or update response.
- `201` means a resource was created.
- `400` means the request itself is invalid.
- `401` means authentication is absent or invalid; `403` means an authenticated caller lacks permission.
- `404` means the route or resource is not found; `500` means an unexpected server failure.

## Debugging Order

Check the URL and method, browser network details, request headers/body, server logs, and finally the database query. This prevents treating every failure as a frontend problem.
