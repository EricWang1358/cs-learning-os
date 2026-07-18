/**
 * 手工构造的示例 GraphExport (LC 300 最长递增子序列 · 问题树), 用于:
 * - 离线开发与 Storybook/Demo 页;
 * - 演示全部视觉编码: 掌握度四态、根节点放大+标注、共享子树双环、PROBLEM_LOCAL 虚线边。
 *
 * 树结构(边方向 = "依赖 → 前置"):
 *   n1 LC 300 LIS (root, FRAGILE)
 *     └─ n2 动态规划 (LEARNING)
 *          ├─ n3 记忆化搜索 (LEARNING) ─┐
 *          └─ n4 状态转移方程 (FRAGILE, 共享×2) ─┤
 *                                             ├─ n5 递归基础 (MASTERED, parentCount=2, 共享×2)
 *                                             └───────────────┘
 *                                                  └─ n6 C++ 内存模型 (UNKNOWN, PROBLEM_LOCAL 私有深挖分支)
 *
 * 共享演示: n4 "状态转移方程" sharedByQuestions=2 (同时挂在另一棵问题树下, 双环高亮);
 * n5 属于该共享子树的可达集, 同样 sharedByQuestions=2。
 * 节点顺序遵循契约的 (layer, id) 稳定排序。
 */

import type { GraphExport } from './types';

export const SAMPLE_LIS_EXPORT: GraphExport = {
  schemaVersion: 1,
  contentHash: 'sha256:demo-lis-9f2c7a1e4b6d8f0a2c4e6a8b0d1f3a5c7e9b1d3f5a7c9e1b3d5f7a9c1e3b5d70',
  generatedAt: 1780000000000,
  nodes: [
    {
      id: 'n1',
      title: 'LC 300 最长递增子序列',
      layer: 0,
      mastery: 'FRAGILE',
      score: 0.4,
      parentCount: 0,
      sharedByQuestions: 1,
      isRoot: true,
    },
    {
      id: 'n2',
      title: '动态规划 (DP)',
      layer: 1,
      mastery: 'LEARNING',
      score: 0.55,
      parentCount: 1,
      sharedByQuestions: 1,
      isRoot: false,
    },
    {
      id: 'n3',
      title: '记忆化搜索',
      layer: 2,
      mastery: 'LEARNING',
      score: 0.5,
      parentCount: 1,
      sharedByQuestions: 1,
      isRoot: false,
    },
    {
      id: 'n4',
      title: '状态转移方程',
      layer: 2,
      mastery: 'FRAGILE',
      score: 0.35,
      parentCount: 1,
      sharedByQuestions: 2,
      isRoot: false,
    },
    {
      id: 'n5',
      title: '递归基础',
      layer: 3,
      mastery: 'MASTERED',
      score: 0.9,
      parentCount: 2,
      sharedByQuestions: 2,
      isRoot: false,
    },
    {
      id: 'n6',
      title: 'C++ 内存模型',
      layer: 4,
      mastery: 'UNKNOWN',
      score: 0,
      parentCount: 1,
      sharedByQuestions: 1,
      isRoot: false,
    },
  ],
  links: [
    { source: 'n1', target: 'n2', scope: 'GLOBAL' },
    { source: 'n2', target: 'n3', scope: 'GLOBAL' },
    { source: 'n2', target: 'n4', scope: 'GLOBAL' },
    { source: 'n3', target: 'n5', scope: 'GLOBAL' },
    { source: 'n4', target: 'n5', scope: 'GLOBAL' },
    { source: 'n5', target: 'n6', scope: 'PROBLEM_LOCAL' },
  ],
};
