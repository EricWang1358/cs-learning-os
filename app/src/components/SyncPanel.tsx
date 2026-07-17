import { useEffect, useState } from 'react'
import type { SyncDevice, SyncHealth, SyncPairingToken } from '../types/api'

const READ_SCOPE = 'sync:read'
const PUSH_SCOPE = 'sync:push'

function formatDateTime(iso?: string) {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function copyToClipboard(text: string) {
  return navigator.clipboard.writeText(text)
}

export type SyncPanelProps = {
  health: SyncHealth | null
  devices: SyncDevice[]
  token: SyncPairingToken | null
  isLoading: boolean
  isGeneratingToken: boolean
  error: string
  onGenerateToken: () => void
  onRevoke: (deviceId: string) => void
  onUpdateScopes: (deviceId: string, scopes: string[]) => void
  onRefresh: () => void
  serverBaseUrl: string
}

export function SyncPanel({
  health,
  devices,
  token,
  isLoading,
  isGeneratingToken,
  error,
  onGenerateToken,
  onRevoke,
  onUpdateScopes,
  onRefresh,
  serverBaseUrl,
}: SyncPanelProps) {
  const [copied, setCopied] = useState<'endpoint' | 'token' | 'payload' | null>(null)

  useEffect(() => {
    if (!copied) return
    const timer = window.setTimeout(() => setCopied(null), 2000)
    return () => window.clearTimeout(timer)
  }, [copied])

  const activeDevices = devices.filter((device) => !device.revokedAt)
  const revokedDevices = devices.filter((device) => device.revokedAt)

  return (
    <div className="health-detail">
      <section className="detail-section" aria-label="Sync status">
        <div className="detail-toolbar">
          <h3>Sync status</h3>
          <button type="button" className="focus-toggle" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
        {error && <p className="error-banner">{error}</p>}
        <div className="health-grid compact-health-grid">
          <article className="health-card">
            <p className="eyebrow">Protocol</p>
            <h3>v{health?.protocolVersion ?? '—'}</h3>
            <p>Desktop↔mobile sync protocol version.</p>
          </article>
          <article className="health-card">
            <p className="eyebrow">Server ID</p>
            <h3 className="mono-text">{health?.serverId?.slice(0, 8) ?? '—'}</h3>
            <p>Resets if the desktop database is recreated.</p>
          </article>
          <article className="health-card">
            <p className="eyebrow">Paired devices</p>
            <h3>{health?.pairedDevices ?? activeDevices.length}</h3>
            <p>{activeDevices.length === 1 ? 'device is trusted.' : 'devices are trusted.'}</p>
          </article>
          <article className="health-card">
            <p className="eyebrow">Endpoint</p>
            <h3 className="mono-text">{serverBaseUrl || '—'}</h3>
            <p>Address your phone connects to.</p>
          </article>
        </div>
      </section>

      <section className="detail-section" aria-label="Pair a new device">
        <h3>Pair a new phone</h3>
        <ol className="numbered-steps">
          <li>Open this CS Learning OS desktop app and keep it running.</li>
          <li>Make sure your phone is on the same network as this computer (or reachable via Tailscale).</li>
          <li>On your phone, open CS Learning OS → More → Sync.</li>
          <li>Click the button below to generate a one-time pairing token (valid for 10 minutes).</li>
          <li>Copy the endpoint and token into the phone, or share the pairing payload.</li>
        </ol>
        <div className="detail-toolbar">
          <button
            type="button"
            className="focus-toggle ai-action"
            onClick={onGenerateToken}
            disabled={isGeneratingToken}
          >
            {isGeneratingToken ? 'Generating...' : 'Generate pairing token'}
          </button>
        </div>
        {token && (
          <div className="sync-token-panel">
            <div className="sync-token-row">
              <label>Endpoint</label>
              <code>{serverBaseUrl || '—'}</code>
              <button
                type="button"
                className="focus-toggle"
                onClick={() => {
                  copyToClipboard(serverBaseUrl).then(() => setCopied('endpoint'))
                }}
              >
                {copied === 'endpoint' ? 'Copied' : 'Copy'}
              </button>
            </div>
            <div className="sync-token-row">
              <label>Token</label>
              <code>{token.token}</code>
              <button
                type="button"
                className="focus-toggle"
                onClick={() => {
                  copyToClipboard(token.token).then(() => setCopied('token'))
                }}
              >
                {copied === 'token' ? 'Copied' : 'Copy'}
              </button>
            </div>
            <div className="sync-token-row">
              <label>Pairing payload</label>
              <code className="long">{token.pairingPayload}</code>
              <button
                type="button"
                className="focus-toggle"
                onClick={() => {
                  copyToClipboard(token.pairingPayload).then(() => setCopied('payload'))
                }}
              >
                {copied === 'payload' ? 'Copied' : 'Copy'}
              </button>
            </div>
            <p className="inline-hint">Expires at {formatDateTime(token.expiresAt)}. Tokens can only be created from the desktop itself.</p>
          </div>
        )}
      </section>

      <section className="detail-section" aria-label="Paired devices">
        <h3>Trusted devices</h3>
        {activeDevices.length === 0 ? (
          <p>No devices paired yet. Generate a token above to pair your phone.</p>
        ) : (
          <div className="job-list">
            {activeDevices.map((device) => (
              <article className="ai-revision-card compact-card" key={device.id}>
                <p className="eyebrow">{device.scopes.join(', ')}</p>
                <h3>{device.name}</h3>
                <p>Created {formatDateTime(device.createdAt)}</p>
                <p>Last seen {formatDateTime(device.lastSeenAt)}</p>
                <div className="sync-device-permissions" aria-label={`Permissions for ${device.name}`}>
                  <label>
                    <input
                      type="checkbox"
                      checked={device.scopes.includes(READ_SCOPE)}
                      disabled={isLoading || (device.scopes.length === 1 && device.scopes.includes(READ_SCOPE))}
                      onChange={(event) => {
                        const next = event.target.checked
                          ? Array.from(new Set([...device.scopes, READ_SCOPE]))
                          : device.scopes.filter((scope) => scope !== READ_SCOPE)
                        onUpdateScopes(device.id, next)
                      }}
                    />
                    Read
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={device.scopes.includes(PUSH_SCOPE)}
                      disabled={isLoading || (device.scopes.length === 1 && device.scopes.includes(PUSH_SCOPE))}
                      onChange={(event) => {
                        const next = event.target.checked
                          ? Array.from(new Set([...device.scopes, PUSH_SCOPE]))
                          : device.scopes.filter((scope) => scope !== PUSH_SCOPE)
                        onUpdateScopes(device.id, next)
                      }}
                    />
                    Upload
                  </label>
                </div>
                <div className="question-card-actions">
                  <button
                    type="button"
                    className="focus-toggle ai-action"
                    onClick={() => onRevoke(device.id)}
                  >
                    Revoke
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
        {revokedDevices.length > 0 && (
          <details>
            <summary>Revoked devices ({revokedDevices.length})</summary>
            <div className="job-list">
              {revokedDevices.map((device) => (
                <article className="ai-revision-card compact-card muted" key={device.id}>
                  <p className="eyebrow">revoked</p>
                  <h3>{device.name}</h3>
                  <p>Created {formatDateTime(device.createdAt)}</p>
                  <p>Revoked {formatDateTime(device.revokedAt)}</p>
                </article>
              ))}
            </div>
          </details>
        )}
      </section>
    </div>
  )
}
