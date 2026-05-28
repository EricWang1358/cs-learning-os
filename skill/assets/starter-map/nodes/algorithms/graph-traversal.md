---
title: "Graph Traversal"
area: algorithms
status: seed
visibility: core
tags: [graph, bfs, dfs]
prerequisites: []
related: [binary-search]
sources:
  - https://cp-algorithms.com/graph/breadth-first-search.html
  - https://cp-algorithms.com/graph/depth-first-search.html
summary: "Visit graph states systematically with BFS or DFS to reveal reachability and structure."
---

# Graph Traversal

## Why It Matters

Many graph problems begin by asking what can be reached, how components are connected, or what order states should be explored.

## Core Idea

BFS explores by distance layers. DFS explores deeply and is often useful for structural properties.

## When To Use It

- Reachability.
- Connected components.
- Shortest path in an unweighted graph.
- Cycle or ordering analysis.

## Common Confusions

- BFS gives shortest path only when each edge has equal cost.
- DFS recursion can overflow on large inputs.

## Suggested Next

- Study Dijkstra after BFS.
- Study topological sorting after DFS.
