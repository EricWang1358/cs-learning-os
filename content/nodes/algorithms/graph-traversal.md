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

# Graph Traversal / 图遍历

## 为什么重要 / Why It Matters

Many graph problems begin by asking what can be reached, how components are connected, or what order states should be explored.

图是计算机科学中最强大的抽象工具之一。社交网络、道路网络、互联网链接、程序依赖关系——这些都可以用图来表示。而图的遍历，就是解决这些问题的基础。

**图的核心问题模式**：
- 从某个起点出发，能到达哪些节点？
- 两个节点之间是否存在路径？
- 最短路径是什么？
- 图是否有环？环在哪里？
- 节点之间的拓扑顺序是什么？

这些问题听起来不同，但都可以用**两种基本的遍历策略**来解决：广度优先搜索（BFS）和深度优先搜索（DFS）。

## 预备知识：图的基本概念 / Prerequisites: Graph Basics

在深入遍历算法之前，确保理解以下概念：

### 图的表示

**邻接表**（Adjacency List）- 常用表示：
```c
// 图的节点编号从 0 到 n-1
#define MAXN 1000

int n;  // 节点数量
int adj[MAXN][MAXN];  // 邻接矩阵
int adj_size[MAXN];   // 每个节点的邻居数量

// 或者使用链表/动态数组
struct Node {
    int val;
    struct Node* next;
};
```

**邻接矩阵 vs 邻接表**：
- 邻接矩阵：适合密集图，`O(1)` 判断两点是否相邻，但占用 O(n²) 空间
- 邻接表：适合稀疏图，占用 O(n+m) 空间（m 是边数），遍历邻居需要 O(degree)

### 图的分类

- **有向图 vs 无向图**：有向图的边有方向，无向图的边双向通行
- **有权图 vs 无权图**：边的权重表示距离、成本等
- **有环图 vs 无环图**：DAG（有向无环图）有特殊性质

## 核心思想 / Core Idea

BFS explores by distance layers. DFS explores deeply and is often useful for structural properties.

### 广度优先搜索（BFS）- 像水波一样扩散

想象你往水里扔一颗石子，水波会一圈一圈地向外扩散。BFS 就是这样工作的：

1. 从起点开始，先访问所有距离为 1 的节点
2. 然后访问所有距离为 2 的节点
3. 然后访问所有距离为 3 的节点
4. ……直到访问完所有可达节点

**BFS 使用队列（Queue）作为核心数据结构**，队列的特性是"先进先出"，正好实现了"按层次访问"的需求。

### 深度优先搜索（DFS）- 一条路走到黑

想象你走迷宫，DFS 的策略是：

1. 选择一条路，一直往前走
2. 遇到死胡同就退回上一个分叉口
3. 尝试另一条路
4. 重复直到所有路都走遍

**DFS 可以使用栈（Stack）或递归实现**。递归实现最直观，栈实现则可以避免递归深度过深的问题。

## BFS 详解 / BFS in Detail

### 算法步骤

```
输入: 图 G = (V, E)，起点 s
输出: 从 s 可达的所有节点

1. 初始化:
   - 创建一个队列 Q
   - 创建一个 visited 数组，初始全部为 false
   - 将 s 标记为已访问，加入队列 Q

2. 当 Q 不为空时，重复:
   a. 从 Q 中取出队首节点 u
   b. 访问 u（可以在这里处理节点）
   c. 对于 u 的每个邻居 v:
      - 如果 v 未被访问:
        - 将 v 标记为已访问
        - 将 v 加入队列 Q
```

### 详细图解

以这个无向图为例：

```
       A
      / \
     B   C
    /|\   \
   D E F   G
```

边的表示：A 连接 B 和 C，B 连接 A、D、E、F，C 连接 A 和 G，D、E、F 只连接 B。

**BFS 遍历步骤**（从 A 开始）：

| 步骤 | 操作 | 队列状态 | 访问顺序 |
|------|------|----------|----------|
| 1 | 访问 A，加入队列 | [A] | A |
| 2 | 取出 A，访问 B、C，加入队列 | [B, C] | A, B |
| 3 | 取出 B，访问 D、E、F，加入队列 | [C, D, E, F] | A, B, C |
| 4 | 取出 C，访问 G，加入队列 | [D, E, F, G] | A, B, C, G |
| 5 | 取出 D，无新邻居 | [E, F, G] | A, B, C, G, D |
| 6 | 取出 E，无新邻居 | [F, G] | A, B, C, G, D, E |
| 7 | 取出 F，无新邻居 | [G] | A, B, C, G, D, E, F |
| 8 | 取出 G，无新邻居 | [] | A, B, C, G, D, E, F, G |

**访问顺序**: A → B → C → G → D → E → F

注意：BFS 的访问顺序是按层次来的，同一层中节点的顺序取决于邻居加入队列的顺序。

### 代码实现

**C 语言实现**：

```c
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>

#define MAXN 1000

int n;  // 节点数量
int adj[MAXN][MAXN];
int adj_size[MAXN];
bool visited[MAXN];

void bfs(int start) {
    int queue[MAXN];
    int front = 0, rear = 0;

    // 将起点加入队列
    visited[start] = true;
    queue[rear++] = start;

    while (front < rear) {
        int u = queue[front++];
        printf("访问节点: %c\n", 'A' + u);

        for (int i = 0; i < adj_size[u]; i++) {
            int v = adj[u][i];
            if (!visited[v]) {
                visited[v] = true;
                queue[rear++] = v;
            }
        }
    }
}
```

**Python 实现**：

```python
from collections import deque

def bfs(graph, start):
    visited = set()
    queue = deque([start])
    visited.add(start)

    while queue:
        u = queue.popleft()
        print(f"访问节点: {u}")

        for v in graph[u]:
            if v not in visited:
                visited.add(v)
                queue.append(v)
```

### BFS 的核心应用：最短路径

BFS 在**无权图**中找到从起点到所有可达节点的最短路径（以边数为单位）。

```c
// BFS + 记录距离和前驱节点
void bfs_shortest_path(int start, int end) {
    int queue[MAXN];
    int front = 0, rear = 0;
    int dist[MAXN];
    int parent[MAXN];

    for (int i = 0; i < n; i++) {
        dist[i] = -1;
        parent[i] = -1;
    }

    visited[start] = true;
    dist[start] = 0;
    queue[rear++] = start;

    while (front < rear) {
        int u = queue[front++];

        for (int i = 0; i < adj_size[u]; i++) {
            int v = adj[u][i];
            if (dist[v] == -1) {
                dist[v] = dist[u] + 1;
                parent[v] = u;
                queue[rear++] = v;
            }
        }
    }

    // 打印最短路径
    if (dist[end] == -1) {
        printf("从 %d 到 %d 不可达\n", start, end);
        return;
    }

    printf("最短距离: %d\n", dist[end]);

    // 通过 parent 回溯路径
    int path[MAXN];
    int path_len = 0;
    for (int v = end; v != -1; v = parent[v]) {
        path[path_len++] = v;
    }
    printf("最短路径: ");
    for (int i = path_len - 1; i >= 0; i--) {
        printf("%d ", path[i]);
    }
    printf("\n");
}
```

**为什么 BFS 能找到最短路径？**

因为 BFS 按层次遍历，第一次到达某个节点时，经过的路径一定是最短的（边数最少）。如果存在更短的路径，那个更短的路径会在更早的层次到达该节点，与"第一次到达"的定义矛盾。

## DFS 详解 / DFS in Detail

### 算法步骤（递归版）

```
输入: 图 G = (V, E)，起点 s
输出: 从 s 可达的所有节点

DFS(u):
1. 标记 u 为已访问
2. 访问 u（可以在这里处理节点）
3. 对于 u 的每个邻居 v:
   - 如果 v 未被访问，递归调用 DFS(v)
```

### 详细图解

使用同样的图：

```
       A
      / \
     B   C
    /|\   \
   D E F   G
```

**DFS 遍历步骤**（从 A 开始，优先访问字母较小的邻居）：

| 递归深度 | 操作 | 调用栈 |
|----------|------|--------|
| 1 | 访问 A | DFS(A) |
| 2 | A 的邻居 B 未访问，调用 DFS(B) | DFS(A) → DFS(B) |
| 3 | 访问 B | DFS(A) → DFS(B) |
| 4 | B 的邻居 D 未访问，调用 DFS(D) | DFS(A) → DFS(B) → DFS(D) |
| 5 | 访问 D，D 无邻居，返回 | DFS(A) → DFS(B) |
| 6 | B 的邻居 E 未访问，调用 DFS(E) | DFS(A) → DFS(B) → DFS(E) |
| 7 | 访问 E，E 无邻居，返回 | DFS(A) → DFS(B) |
| 8 | B 的邻居 F 未访问，调用 DFS(F) | DFS(A) → DFS(B) → DFS(F) |
| 9 | 访问 F，F 无邻居，返回 | DFS(A) → DFS(B) |
| 10 | B 处理完毕，返回 A | DFS(A) |
| 11 | A 的邻居 C 未访问，调用 DFS(C) | DFS(A) → DFS(C) |
| 12 | 访问 C | DFS(A) → DFS(C) |
| 13 | C 的邻居 G 未访问，调用 DFS(G) | DFS(A) → DFS(C) → DFS(G) |
| 14 | 访问 G，G 无邻居，返回 | DFS(A) → DFS(C) |
| 15 | C 处理完毕，返回 A | DFS(A) |
| 16 | A 处理完毕，结束 | - |

**访问顺序**: A → B → D → E → F → C → G

### 代码实现

**C 语言实现（递归版）**：

```c
bool visited[MAXN];

void dfs(int u) {
    visited[u] = true;
    printf("访问节点: %c\n", 'A' + u);

    for (int i = 0; i < adj_size[u]; i++) {
        int v = adj[u][i];
        if (!visited[v]) {
            dfs(v);
        }
    }
}
```

**C 语言实现（栈版，避免递归深度问题）**：

```c
void dfs_iterative(int start) {
    int stack[MAXN];
    int top = 0;

    visited[start] = true;
    stack[top++] = start;

    while (top > 0) {
        int u = stack[--top];
        printf("访问节点: %c\n", 'A' + u);

        for (int i = 0; i < adj_size[u]; i++) {
            int v = adj[u][i];
            if (!visited[v]) {
                visited[v] = true;
                stack[top++] = v;
            }
        }
    }
}
```

**Python 实现（递归版）**：

```python
def dfs_recursive(graph, node, visited=None):
    if visited is None:
        visited = set()

    visited.add(node)
    print(f"访问节点: {node}")

    for neighbor in graph[node]:
        if neighbor not in visited:
            dfs_recursive(graph, neighbor, visited)
```

### DFS 的核心应用

#### 1. 连通分量检测

```c
// 计算无向图的连通分量数量
int connected_components() {
    int count = 0;
    memset(visited, false, sizeof(visited));

    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            printf("=== 分量 %d ===\n", ++count);
            dfs(i);
        }
    }
    return count;
}
```

#### 2. 环检测

```c
// 检测无向图是否有环
bool has_cycle_undirected() {
    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            if (dfs_cycle(i, -1)) {
                return true;
            }
        }
    }
    return false;
}

bool dfs_cycle(int u, int parent) {
    visited[u] = true;

    for (int i = 0; i < adj_size[u]; i++) {
        int v = adj[u][i];
        if (!visited[v]) {
            if (dfs_cycle(v, u)) {
                return true;
            }
        } else if (v != parent) {
            // 邻居已访问且不是父节点，说明有环
            return true;
        }
    }
    return false;
}
```

#### 3. 拓扑排序（仅适用于 DAG）

```c
// DFS 版本的拓扑排序
int topo_order[MAXN];
int topo_count = 0;

void dfs_topo(int u) {
    visited[u] = true;

    for (int i = 0; i < adj_size[u]; i++) {
        int v = adj[u][i];
        if (!visited[v]) {
            dfs_topo(v);
        }
    }

    // 后序记录：所有邻居处理完毕后才加入拓扑序列
    topo_order[topo_count++] = u;
}

void topological_sort() {
    memset(visited, false, sizeof(visited));
    topo_count = 0;

    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            dfs_topo(i);
        }
    }

    // 逆序即为拓扑排序结果
    printf("拓扑排序: ");
    for (int i = topo_count - 1; i >= 0; i--) {
        printf("%d ", topo_order[i]);
    }
    printf("\n");
}
```

## BFS vs DFS / BFS 与 DFS 的对比

| 特性 | BFS | DFS |
|------|-----|-----|
| 数据结构 | 队列（Queue） | 栈（Stack）或递归 |
| 访问顺序 | 按层次（距离递增） | 深入一条路径直到尽头 |
| 空间复杂度 | O(n) 最坏情况（宽而浅） | O(n) 最坏情况（深而窄） |
| 找到的第一条路径 | 最短路径（无权图） | 不一定最短 |
| 适用场景 | 最短路径、层次遍历 | 连通分量、环检测、拓扑排序 |
| 递归深度问题 | 无 | 大图可能导致栈溢出 |

**选择指南**：
- 需要找最短路径？→ BFS
- 需要遍历所有节点？→ 两者皆可
- 图可能很深（树形结构）？→ BFS 防止栈溢出
- 检测环、连通分量？→ DFS 更直观
- 拓扑排序？→ DFS（后序逆置）或 Kahn 算法（BFS）

## 常见错误与陷阱 / Common Mistakes and Pitfalls

### 陷阱 1：忘记标记已访问

**错误代码**：
```c
void dfs(int u) {
    printf("访问节点: %d\n", u);
    for (int i = 0; i < adj_size[u]; i++) {
        int v = adj[u][i];
        dfs(v);  // 没有检查 visited！
    }
}
```

这会导致无限循环（在有环图中）或重复访问（在无环图中）。

**正确做法**：在递归调用前检查并标记 `visited`。

### 陷阱 2：BFS 中队列操作顺序错误

在 BFS 中，确保在**加入队列时**标记为已访问，而不是**取出队列时**。否则可能导致同一节点被多次加入队列。

**错误**：
```c
queue[rear++] = v;  // 加入队列
visited[v] = true; // 太晚了！
```

**正确**：
```c
if (!visited[v]) {
    visited[v] = true;  // 加入前就标记
    queue[rear++] = v;
}
```

### 陷阱 3：递归 DFS 的栈溢出

对于深度可能很大的图（如链状图），递归 DFS 会耗尽栈空间。

**解决方案**：使用显式栈的迭代版本，或增加栈大小限制。

### 陷阱 4：拓扑排序中忘记处理未访问节点

如果图不连通，只从某一个节点开始 DFS 可能无法遍历所有节点。确保在主循环中遍历所有节点。

## 识别信号 / Recognition Signals

以下情况提示你可能需要使用图遍历：

- **节点和边的关系**：问题涉及"连接"、"可达"、"路径"
- **层次结构**：需要按距离或级别处理
- **连通性**：需要判断图是否连通，或有多少个连通分量
- **路径问题**：最短路径、最长路径、所有路径
- **环和顺序**：检测环、拓扑排序、依赖顺序

## 扩展阅读 / Extensions

- **Dijkstra 算法**：BFS 的加权版本，用于有权图的最短路径
- **A* 搜索**：启发式搜索，结合 BFS 的层次性和贪心的目标导向
- **Kosaraju / Tarjan 算法**：找强连通分量
- **Prim / Kruskal 算法**：生成树相关

## 练习建议 / Practice Suggestions

### 简单难度
- LeetCode 104: 二叉树的最大深度（DFS/BFS）
- LeetCode 111: 二叉树的最小深度（BFS）
- LeetCode 101: 对称二叉树（DFS）

### 中等难度
- LeetCode 200: 岛屿数量（BFS/DFS 网格遍历）
- LeetCode 133: 克隆图（BFS + 哈希表）
- LeetCode 207: 课程表（拓扑排序）

### 困难难度
- LeetCode 269: 火星词典（拓扑排序变体）
- LeetCode 847: 访问所有节点的最短路径（BFS + 状态压缩）
- LeetCode 332: 重新安排行程（Hierholzer 算法）
