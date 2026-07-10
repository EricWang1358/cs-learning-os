---
title: "BFS versus DFS"
area: algorithms
track: graph-patterns
status: demo
visibility: practice
difficulty: easy
tags: [graph, bfs, dfs]
linked_nodes: [graph-traversal]
sources: []
summary: "Choose BFS or DFS from the actual graph property the problem asks for."
---

# BFS versus DFS

## Prompt

In an unweighted grid, you need the fewest moves from a start cell to an exit. Which traversal should you use, and when would DFS be a better first choice instead?

## Answer

Use BFS because it visits cells by increasing number of moves from the start; the first time it reaches the exit is therefore a shortest path. DFS is a better first choice for tasks such as finding connected components, checking whether a path exists, detecting a cycle, or exploring a recursive structure where shortest distance is not required.

## Explanation

BFS uses a queue, which keeps all distance-one states ahead of distance-two states and so on. DFS uses a stack or recursion and can reach a very long route before noticing a shorter one. Both visit a graph in linear time, but their visit order gives them different guarantees and different natural applications.
