---
title: "Graph Traversal"
area: algorithms
track: graph-patterns
order: 20
status: demo
visibility: core
tags: [graph, bfs, dfs, shortest-path]
prerequisites: []
related: [binary-search, two-pointers]
sources: []
summary: "Choose BFS or DFS to explore reachability, components, paths, and dependencies."
---

# Graph Traversal

## Why It Matters

Many interview problems are graphs in disguise: a grid, prerequisites, a social network, a file tree, or a state machine. Start by identifying vertices, edges, and whether the question asks about reachability, distance, or structure.

## BFS and DFS

**Breadth-first search (BFS)** visits states in increasing edge distance. With an unweighted graph, the first time BFS reaches a vertex is a shortest path in number of edges. Its core data structure is a queue.

**Depth-first search (DFS)** follows one branch before backtracking. It is a natural fit for connected components, cycle detection, recursive trees, and topological-order reasoning. It uses recursion or an explicit stack.

```python
from collections import deque

def bfs(graph, start):
    seen = {start}
    queue = deque([start])
    while queue:
        node = queue.popleft()
        for neighbor in graph[node]:
            if neighbor not in seen:
                seen.add(neighbor)
                queue.append(neighbor)
```

## Selection Guide

- Need the shortest number of moves in an unweighted graph: BFS.
- Need to explore a connected region, detect a cycle, or process a dependency graph: DFS is often simpler.
- Need every disconnected component: loop over every vertex and start a traversal from each unseen vertex.

## Common Mistakes

Mark a vertex seen when enqueuing or pushing it, not when removing it. Otherwise multiple parents can schedule the same vertex. In a recursive DFS, confirm the input size cannot overflow the call stack; an explicit stack is safer for deep graphs.
