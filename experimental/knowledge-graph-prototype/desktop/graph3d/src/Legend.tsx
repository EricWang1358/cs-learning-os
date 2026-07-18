/**
 * 图例: 掌握度四态色板 + 共享节点双环 + 根节点 + 边线型(GLOBAL 实线 / PROBLEM_LOCAL 虚线)。
 * 纯展示组件, 颜色全部取自 nodePresentation, 保证与 3D 场景同源。
 */

import React from 'react';
import { MASTERY_STATES } from './types';
import { GRAPH_THEME, MASTERY_VISUALS, linkStyleSamples } from './nodePresentation';

export interface LegendProps {
  className?: string;
  style?: React.CSSProperties;
}

const containerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 7,
  padding: '10px 12px',
  background: GRAPH_THEME.panelBackground,
  border: `1px solid ${GRAPH_THEME.panelBorder}`,
  borderRadius: 10,
  fontSize: 12,
  lineHeight: 1.4,
  color: GRAPH_THEME.textPrimary,
  fontFamily: '"PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
  boxShadow: '0 2px 10px rgba(90, 80, 60, 0.08)',
  userSelect: 'none',
};

const rowStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  whiteSpace: 'nowrap',
};

const glyphBoxStyle: React.CSSProperties = {
  width: 18,
  display: 'inline-flex',
  justifyContent: 'center',
  alignItems: 'center',
  flex: 'none',
};

const titleStyle: React.CSSProperties = {
  fontWeight: 600,
  fontSize: 12,
  color: GRAPH_THEME.textPrimary,
};

const descStyle: React.CSSProperties = {
  color: GRAPH_THEME.textSecondary,
  fontSize: 11,
};

/** 图例(无 props 依赖, 渲染开销可忽略, memo 仅为防御父级高频重渲染) */
export const Legend = React.memo(function Legend({ className, style }: LegendProps): React.ReactElement {
  return (
    <div className={className} style={{ ...containerStyle, ...style }}>
      <div style={titleStyle}>掌握度</div>
      {MASTERY_STATES.map((state) => {
        const visual = MASTERY_VISUALS[state];
        return (
          <div key={state} style={rowStyle} title={visual.description}>
            <span style={glyphBoxStyle}>
              <span
                style={{
                  width: 11,
                  height: 11,
                  borderRadius: '50%',
                  background: visual.color,
                  boxShadow: visual.haloScale > 0 ? `0 0 5px 1px ${visual.color}` : 'none',
                  display: 'inline-block',
                }}
              />
            </span>
            <span>{visual.label}</span>
            <span style={descStyle}>{state}</span>
          </div>
        );
      })}

      <div style={{ ...titleStyle, marginTop: 4 }}>标识</div>
      <div style={rowStyle} title="被 ≥2 棵问题树引用的共享子树根">
        <span style={glyphBoxStyle}>
          <span
            style={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              background: '#d2a24c',
              border: `2px solid ${GRAPH_THEME.sharedRingColor}`,
              outline: `2px solid ${GRAPH_THEME.sharedRingColor}`,
              outlineOffset: 2,
              display: 'inline-block',
              boxSizing: 'border-box',
            }}
          />
        </span>
        <span>共享子树</span>
        <span style={descStyle}>双环 · sharedByQuestions ≥ 2</span>
      </div>
      <div style={rowStyle} title="当前导出的根节点">
        <span style={glyphBoxStyle}>
          <span
            style={{
              width: 14,
              height: 14,
              borderRadius: '50%',
              background: '#c05f4e',
              border: `2px solid ${GRAPH_THEME.textPrimary}`,
              display: 'inline-block',
              boxSizing: 'border-box',
            }}
          />
        </span>
        <span>根节点</span>
        <span style={descStyle}>加大 + 文字标注</span>
      </div>

      <div style={{ ...titleStyle, marginTop: 4 }}>边</div>
      {linkStyleSamples().map(({ scope, style: ls }) => (
        <div key={scope} style={rowStyle} title={scope === 'GLOBAL' ? '全局共享边' : '问题私有边'}>
          <span style={glyphBoxStyle}>
            <span
              style={{
                width: 22,
                height: 0,
                borderTop: ls.dashed ? `2px dashed ${ls.color}` : `2px solid ${ls.color}`,
                display: 'inline-block',
              }}
            />
          </span>
          <span>{scope}</span>
          <span style={descStyle}>{ls.dashed ? '虚线 · 本题私有' : '实线 · 全局共享'}</span>
        </div>
      ))}
    </div>
  );
});
