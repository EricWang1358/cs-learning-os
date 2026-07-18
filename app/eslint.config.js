import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  // KnowledgeGraphPanel performs async API loading from an effect. The state
  // updates happen in the async completion path, but the React compiler rule
  // cannot distinguish that boundary from a synchronous cascading update.
  {
    files: ['src/components/KnowledgeGraphPanel.tsx'],
    rules: {
      'react-hooks/set-state-in-effect': 'off',
    },
  },
  // react-force-graph-3d owns a mutable runtime graph: it injects x/y/z and
  // source/target references, while the camera and simulation are controlled
  // through imperative refs. These mutations are isolated to this adapter and
  // are required by the third-party engine contract.
  {
    files: ['src/graph3d/KnowledgeGraph3D.tsx'],
    rules: {
      'react-hooks/immutability': 'off',
      'react-hooks/refs': 'off',
      'react-hooks/set-state-in-effect': 'off',
    },
  },
])
