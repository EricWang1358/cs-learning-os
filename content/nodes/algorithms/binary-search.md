---
title: "Binary Search"
area: algorithms
status: seed
visibility: core
tags: [search, monotonicity, optimization]
prerequisites: []
related: [graph-traversal]
sources:
  - https://cp-algorithms.com/num_methods/binary_search.html
summary: "Use a monotonic condition to locate a boundary in logarithmic time."
---

# Binary Search

## 为什么重要 / Why It Matters

Binary search is not only for sorted arrays. It is a general method for finding a boundary when the answer space has a monotonic true/false structure.

很多人第一次接触二分搜索时，以为它只是"在有序数组里找一个数"。这个理解没错，但它远远低估了二分搜索的威力。二分搜索的本质是：**在一个只有"否"和"是"的答案空间里，每次问一个问题，就能扔掉一半错误答案**。

这种能力在计算中极为珍贵。假设你有 10 亿个元素，线性查找需要最多 10 亿次比较，而二分搜索最多只需要 30 次（因为 log₂(10⁹) ≈ 30）。这是一个从"百万"到"个位数"的质变。

更关键的是，二分搜索并不局限于数组。只要答案空间可以**有序排列**，且你能判断"答案是向左还是向右"，二分搜索就适用。这使得它成为解决"在单调条件下找最优值"问题的利器。

## 核心思想 / Core Idea

If a predicate changes from false to true only once, repeatedly test the middle and discard half of the search space.

想象你正在玩一个猜数字游戏。我心里想一个 1 到 100 之间的整数，你需要猜出这个数字是什么。每次猜完后我会告诉你"太大了"、"太小了"或"猜对了"。最优策略是什么？

答案是每次猜中间的数字。如果你猜 50，我说"太小了"，那么 1-49 全部排除，只剩 50-100。如果下次你猜 75，我说"太大了"，那么 76-100 全部排除，只剩 50-75。每一次猜测，你的搜索范围缩小一半。

这就是二分搜索的基本思路：**每次取中间位置，比较它与目标的关系，然后丢弃一半不可能包含答案的区间**。

## 直观理解：为什么一半一半地扔？ / Intuition: Why Discard Half Each Time?

让我们用一个具体的例子来建立直觉。假设你在一个包含 15 个元素的有序数组中查找数字 `7`：

```
索引:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14
数组:   [1] [2] [3] [4] [5] [6] [7] [8] [9] [10] [11] [12] [13] [14]
```

**第一次**：检查中间位置 `mid = (0 + 14) / 2 = 7`，即元素 `8`。比较：`8 > 7`，所以 `8` 以及它右边的所有元素（9-14）都不可能是答案。扔掉这 8 个元素，搜索范围缩小到索引 0-6。

**第二次**：现在搜索范围是 `[1,2,3,4,5,6]`，`mid = (0 + 6) / 2 = 3`，即元素 `4`。比较：`4 < 7`，所以 `4` 以及它左边的所有元素（1-3）都不可能是答案。扔掉这 4 个元素，搜索范围缩小到索引 4-6。

**第三次**：搜索范围是 `[5,6]`，`mid = (0 + 6) / 2 = 3` → 等等，这里有个微妙的地方。我们的新范围是索引 4-6，所以 `mid` 应该是 `4 + (6-4)/2 = 5`。即元素 `6`。比较：`6 < 7`，扔掉索引 4-5。

**第四次**：搜索范围是 `[6]`，`mid = 6`，即元素 `7`。找到目标！

注意：我们只用了 4 次比较就找到了目标，而线性搜索最多需要 7 次。对于 15 个元素，log₂(15) ≈ 3.9，向上取整是 4，这与我们观察到的结果一致。

## 算法步骤 / Algorithm Steps

标准的二分搜索查找算法如下：

```
输入: 一个升序排列的数组 arr[0..n-1]，以及目标值 target
输出: target 在数组中的索引，如果不存在则返回 -1

1. 初始化 left = 0, right = n - 1
2. 当 left <= right 时，重复以下步骤：
   a. 计算 mid = left + (right - left) / 2  （防止 left + right 溢出）
   b. 如果 arr[mid] == target，返回 mid
   c. 如果 arr[mid] < target，说明目标在右半边，left = mid + 1
   d. 如果 arr[mid] > target，说明目标在左半边，right = mid - 1
3. 如果循环结束还没找到，返回 -1
```

关键细节解释：

- **`left + (right - left) / 2` 而不是 `(left + right) / 2`**：当 `left` 和 `right` 都是很大的数时，`left + right` 可能溢出整数范围。在 C/Java 等语言中这是真实存在的问题。
- **`left <= right` 而不是 `left < right`**：当 `left == right` 时，搜索区间里还有一个元素，必须检查它。
- **`right = mid - 1` 而不是 `right = mid`**：因为我们已经检查过 `mid` 位置的元素，它不是答案，不需要再包含它。

## 代码实现 / Implementation

### C 语言实现

```c
int binary_search(int arr[], int n, int target) {
    int left = 0;
    int right = n - 1;

    while (left <= right) {
        int mid = left + (right - left) / 2;

        if (arr[mid] == target) {
            return mid;
        } else if (arr[mid] < target) {
            left = mid + 1;
        } else {
            right = mid - 1;
        }
    }

    return -1;
}
```

### Python 实现

```python
def binary_search(arr, target):
    left, right = 0, len(arr) - 1

    while left <= right:
        mid = left + (right - left) // 2

        if arr[mid] == target:
            return mid
        elif arr[mid] < target:
            left = mid + 1
        else:
            right = mid - 1

    return -1
```

### C++ 实现（使用标准库）

```cpp
#include <vector>
#include <algorithm>

int binary_search(std::vector<int>& arr, int target) {
    auto it = std::lower_bound(arr.begin(), arr.end(), target);
    if (it != arr.end() && *it == target) {
        return std::distance(arr.begin(), it);
    }
    return -1;
}
```

## 正确性证明 / Correctness Proof

二分搜索的正确性可以通过**循环不变式**（loop invariant）来证明。

**循环不变式**：在每次循环开始时，如果目标元素存在于数组中，那么它一定在 `[left, right]` 区间内。

**初始化**（第一次循环前）：
- `left = 0`，`right = n - 1`
- 整个数组都在 `[left, right]` 范围内，所以不变式成立

**保持**（每次循环迭代）：
- 我们计算 `mid` 并比较 `arr[mid]` 与 `target`
- 三种情况：
  1. `arr[mid] == target`：找到目标，返回索引，不变式不再需要
  2. `arr[mid] < target`：目标不可能在 `mid` 或其左边（因为数组升序），所以目标如果存在必在 `[mid+1, right]`。我们设置 `left = mid + 1`，不变式保持。
  3. `arr[mid] > target`：目标不可能在 `mid` 或其右边，所以目标如果存在必在 `[left, mid-1]`。我们设置 `right = mid - 1`，不变式保持。

**终止**（循环结束时）：
- 循环结束条件是 `left > right`
- 根据不变式，如果目标存在，它必须在 `[left, right]` 区间内
- 但 `left > right` 意味着这个区间为空，所以目标不存在
- 此时返回 `-1` 是正确的

## 时间与空间复杂度 / Time and Space Complexity

**时间复杂度**：每次迭代将搜索区间缩小一半。经过 k 次迭代后，区间大小约为 n/2^k。当区间大小变为 1 时，k ≈ log₂(n)。因此时间复杂度是 **O(log n)**。

具体推导：
- 第 1 次迭代后：最多 n/2 个元素可能包含答案
- 第 2 次迭代后：最多 n/4 个元素可能包含答案
- 第 k 次迭代后：最多 n/2^k 个元素可能包含答案
- 当 n/2^k ≤ 1 时，即 k ≥ log₂(n)，搜索完成

**空间复杂度**：使用常数额外空间 **O(1)**。无论输入多大，只需要保存 `left`、`right`、`mid` 三个变量。

## 常见变体 / Common Variations

### 1. 查找左边界（Lower Bound）

找到第一个**大于等于**目标值的位置，即使目标值不存在于数组中。

```c
int lower_bound(int arr[], int n, int target) {
    int left = 0, right = n;
    while (left < right) {
        int mid = left + (right - left) / 2;
        if (arr[mid] < target) {
            left = mid + 1;
        } else {
            right = mid;
        }
    }
    return left;  // 注意：返回 left，不是 -1
}
```

**注意**：这里 `right` 初始化为 `n`（而不是 `n-1`），且循环条件是 `left < right`（而不是 `left <= right`）。这确保了当目标插入到所有元素之后时，可以返回 `n`。

### 2. 查找右边界（Upper Bound）

找到最后一个**小于等于**目标值的位置。

```c
int upper_bound(int arr[], int n, int target) {
    int left = 0, right = n;
    while (left < right) {
        int mid = left + (right - left) / 2;
        if (arr[mid] <= target) {
            left = mid + 1;
        } else {
            right = mid;
        }
    }
    return left - 1;  // 返回最后一个小于等于 target 的位置
}
```

### 3. 二分搜索答案（Binary Search on Answer）

当我们要优化的目标本身是单调的，可以对答案空间进行二分。

**典型问题模式**："在满足某条件的最小/最大值是多少？"

例如：给定速度数组 `speeds[]`，找到能够 D 天内到达的最小速度。

```c
bool canFinish(int speeds[], int n, int days, int speed) {
    long total = 0;
    for (int i = 0; i < n; i++) {
        total += (speeds[i] + speed - 1) / speed;  // 向上取整
    }
    return total <= days;
}

int minSpeed(int speeds[], int n, int days) {
    int left = 1, right = 1e7;  // 假设速度上限
    while (left < right) {
        int mid = left + (right - left) / 2;
        if (canFinish(speeds, n, days, mid)) {
            right = mid;
        } else {
            left = mid + 1;
        }
    }
    return left;
}
```

## 常见错误与陷阱 / Common Mistakes and Pitfalls

### 陷阱 1：整数溢出

**错误写法**：
```c
int mid = (left + right) / 2;  // left + right 可能溢出！
```

**正确写法**：
```c
int mid = left + (right - left) / 2;
```

### 陷阱 2：循环条件写错

**错误写法**（会漏掉最后一个元素）：
```c
while (left < right) {  // 当 left == right 时退出，可能漏掉 mid
    ...
}
```

**正确写法**：
```c
while (left <= right) {
    ...
}
```

### 陷阱 3：边界更新错误

**错误写法**（可能死循环）：
```c
if (arr[mid] < target) {
    left = mid;  // 错误：mid 可能等于 left，导致死循环
}
```

**正确写法**：
```c
if (arr[mid] < target) {
    left = mid + 1;  // 一定要 +1，确保区间缩小
}
```

### 陷阱 4：使用有符号整数处理无符号比较

当处理长度很大的数组时，确保使用足够大的整数类型。

## 识别信号 / Recognition Signals

以下情况提示你可能需要使用二分搜索：

- **有序或单调条件**：数据已排序，或问题具有"如果 x 可行，则所有更大的值都可行"的单调性
- **对数时间要求**：问题要求 O(log n) 或更快的时间复杂度
- **答案空间是数值范围**：如"找到最小的满足条件的值"
- **搜索空间巨大**：如在 10⁹ 个元素中查找，线性搜索不可接受

## 何时不用二分搜索 / When Not to Use

- 数据无序且不能排序
- 需要频繁插入/删除（考虑平衡二叉树）
- 只比较一次（线性扫描可能更简单）
- 数据量很小（O(n) 和 O(log n) 差异可忽略）

## 相关主题 / Related Topics

- **图遍历**：二分搜索是一维空间中的"导航"，图遍历是更通用的空间探索
- **二叉搜索树**：二分搜索思想在动态数据上的应用
- **两指针**：当单调性是局部而非全局时的替代方案

## 练习建议 / Practice Suggestions

### 简单难度
- 在排序数组中查找元素，返回索引或 -1
- 实现 `lower_bound` 和 `upper_bound`

### 中等难度
- 在旋转排序数组中查找元素（LeetCode 33）
- 寻找两个有序数组的中位数（LeetCode 4）

### 困难难度
- 在二维矩阵中查找（LeetCode 74）
- 找到最小值在旋转排序数组中的位置（LeetCode 153）
