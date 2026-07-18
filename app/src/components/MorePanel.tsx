import { useEffect, useState } from 'react'

interface AiProvider {
  id: string
  label: string
  provider: string
  apiKey: string
  baseUrl: string
  model: string
  isBuiltIn: boolean
}

interface AiConfigResponse {
  active: string
  activeModel: string
  providers: AiProvider[]
  aiEnabled: boolean
}

type ConnStatus = null | { ok: boolean; message: string; modelCount?: number }

export function MorePanel() {
  const [config, setConfig] = useState<AiConfigResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // New provider form
  const [showNewForm, setShowNewForm] = useState(false)
  const [newLabel, setNewLabel] = useState('')
  const [newApiKey, setNewApiKey] = useState('')
  const [newBaseUrl, setNewBaseUrl] = useState('https://api.deepseek.com/v1')
  const [newModel, setNewModel] = useState('')

  // Edit mode
  const [editingId, setEditingId] = useState<string | null>(null)

  // Test / pull state
  const [connStatus, setConnStatus] = useState<ConnStatus>(null)
  const [pulledModels, setPulledModels] = useState<string[]>([])
  const [busy, setBusy] = useState(false)

  const fetchConfig = async () => {
    try {
      const res = await fetch('/api/system/ai-config')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      setConfig(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load AI config')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchConfig() }, [])

  const testConnection = async (baseUrl: string, apiKey: string) => {
    setBusy(true)
    setConnStatus(null)
    try {
      const res = await fetch('/api/system/ai-config/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ baseUrl, apiKey }),
      })
      const data = await res.json()
      setConnStatus(data)
    } catch (e) {
      setConnStatus({ ok: false, message: e instanceof Error ? e.message : 'Test failed' })
    } finally {
      setBusy(false)
    }
  }

  const pullModels = async (baseUrl: string, apiKey: string) => {
    setBusy(true)
    setConnStatus(null)
    try {
      const res = await fetch('/api/system/ai-config/models', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ baseUrl, apiKey }),
      })
      const data = await res.json()
      if (data.ok) {
        setPulledModels(data.models)
        setConnStatus({ ok: true, message: `Found ${data.count} models`, modelCount: data.count })
      } else {
        setConnStatus({ ok: false, message: data.message || 'Pull failed' })
        setPulledModels([])
      }
    } catch (e) {
      setConnStatus({ ok: false, message: e instanceof Error ? e.message : 'Pull failed' })
    } finally {
      setBusy(false)
    }
  }

  const saveProvider = async () => {
    const id = editingId || newLabel.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '')
    if (!id) { setNotice('Enter a label to generate a provider id.'); return }
    try {
      const res = await fetch('/api/system/ai-config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, label: newLabel || id, apiKey: newApiKey, baseUrl: newBaseUrl, model: newModel }),
      })
      if (!res.ok) { const d = await res.json(); throw new Error((d as any).detail || 'Save failed') }
      setShowNewForm(false); setEditingId(null)
      setNewLabel(''); setNewApiKey(''); setNewBaseUrl('https://api.deepseek.com/v1'); setNewModel('')
      setNotice('Provider saved.')
      fetchConfig()
    } catch (e) { setNotice(e instanceof Error ? e.message : 'Save failed') }
  }

  const activateProvider = async (id: string) => {
    try {
      const res = await fetch('/api/system/ai-config/activate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id }),
      })
      if (!res.ok) { const d = await res.json(); throw new Error((d as any).detail || 'Activation failed') }
      setNotice(`Activated '${id}'. Restart for full effect.`)
      fetchConfig()
    } catch (e) { setNotice(e instanceof Error ? e.message : 'Activation failed') }
  }

  const deleteProvider = async (id: string) => {
    if (!confirm(`Remove provider '${id}'?`)) return
    try {
      await fetch(`/api/system/ai-config/${encodeURIComponent(id)}`, { method: 'DELETE' })
      setNotice('Provider removed.')
      fetchConfig()
    } catch (e) { setNotice(e instanceof Error ? e.message : 'Delete failed') }
  }

  const editProvider = (p: AiProvider) => {
    setEditingId(p.id)
    setNewLabel(p.label)
    setNewApiKey(p.apiKey)
    setNewBaseUrl(p.baseUrl)
    setNewModel(p.model)
    setShowNewForm(true)
    setConnStatus(null); setPulledModels([])
  }

  if (loading) return <section className="more-panel"><p className="detail-loading">Loading…</p></section>
  if (error) return <section className="more-panel"><p className="error-banner">{error}</p></section>

  return (
    <section className="more-panel" aria-label="More settings">
      <header>
        <h2>More</h2>
        <p>Configure AI providers, appearance, and system utilities.</p>
      </header>

      {notice && (
        <p className="inline-hint" role="status">
          {notice}
          <button type="button" onClick={() => setNotice('')} style={{ marginLeft: 8, cursor: 'pointer', background: 'none', border: 'none', color: 'var(--accent)' }}>Dismiss</button>
        </p>
      )}

      {/* AI Provider Section */}
      <section className="more-section" aria-label="AI providers">
        <div className="more-section-heading">
          <h3>AI Provider</h3>
          <span className="more-section-badge">{config?.active ?? 'codex-cli'}</span>
        </div>
        <p className="more-section-desc">
          Manage AI service providers. The active provider powers assistant answers, draft generation, and content review. OpenAI-compatible APIs (DeepSeek, Kimi, Groq, Ollama) are supported.
        </p>

        {connStatus && (
          <div className={`ai-status${connStatus.ok ? '' : ' error'}`} style={{ marginBottom: 12 }}>
            {connStatus.message}
          </div>
        )}

        <ul className="more-provider-list">
          {(config?.providers ?? []).map((p) => {
            const isActive = p.id === config?.active
            return (
              <li key={p.id} className={`more-provider-row${isActive ? ' active' : ''}`}>
                <div className="more-provider-info">
                  <strong>{p.label}</strong>
                  {p.isBuiltIn && <span className="more-badge builtin">built-in</span>}
                  {isActive && <span className="more-badge active">active</span>}
                  <span className="more-provider-detail">
                    {p.provider === 'codex-cli' ? 'Codex CLI' : (p.baseUrl || 'OpenAI compatible')} · {p.model || 'model not set'}
                  </span>
                </div>
                <div className="more-provider-actions">
                  {!p.isBuiltIn && (
                    <>
                      <button type="button" className="more-btn small" onClick={() => testConnection(p.baseUrl, p.apiKey)} disabled={busy || !p.apiKey || !p.baseUrl}>
                        Test
                      </button>
                      <button type="button" className="more-btn small" onClick={() => pullModels(p.baseUrl, p.apiKey)} disabled={busy || !p.apiKey || !p.baseUrl}>
                        Models
                      </button>
                      <button type="button" className="more-btn small" onClick={() => editProvider(p)}>Edit</button>
                    </>
                  )}
                  {!isActive && (
                    <button type="button" className="more-btn small primary" onClick={() => activateProvider(p.id)}>Activate</button>
                  )}
                  {!p.isBuiltIn && (
                    <button type="button" className="more-btn small danger" onClick={() => deleteProvider(p.id)}>Remove</button>
                  )}
                </div>
                {/* Show pulled model picker if this provider was the one we pulled for */}
                {pulledModels.length > 0 && p.baseUrl === newBaseUrl && (
                  <div style={{ width: '100%', marginTop: 8 }}>
                    <p className="more-field-hint" style={{ margin: '0 0 6px' }}>Available models (click to select):</p>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                      {pulledModels.slice(0, 12).map(m => (
                        <button key={m} type="button" className={`more-btn small ${m === p.model ? 'primary' : ''}`}
                          onClick={() => {
                            editProvider(p)
                            setNewModel(m)
                          }}
                        >{m}</button>
                      ))}
                    </div>
                  </div>
                )}
              </li>
            )
          })}
        </ul>

        {!showNewForm && (
          <button type="button" className="more-btn" onClick={() => { setEditingId(null); setNewLabel(''); setNewApiKey(''); setNewBaseUrl('https://api.deepseek.com/v1'); setNewModel(''); setShowNewForm(true); setConnStatus(null); setPulledModels([]) }}>
            + Add OpenAI-compatible provider
          </button>
        )}

        {showNewForm && (
          <div className="more-form">
            <h4>{editingId ? 'Edit provider' : 'New provider'}</h4>
            <label>
              <span>Label (display name)</span>
              <input value={newLabel} onChange={e => setNewLabel(e.target.value)} placeholder="DeepSeek, Kimi, etc." />
            </label>
            <label>
              <span>API Key</span>
              <input type="password" value={newApiKey} onChange={e => setNewApiKey(e.target.value)} placeholder="sk-..." />
            </label>
            <label>
              <span>Base URL</span>
              <input value={newBaseUrl} onChange={e => setNewBaseUrl(e.target.value)} placeholder="https://api.deepseek.com/v1" />
              <span className="more-field-hint">OpenAI-compatible endpoint with trailing /v1.</span>
            </label>
            <label>
              <span>Model</span>
              <input value={newModel} onChange={e => setNewModel(e.target.value)} placeholder="deepseek-v4-flash or deepseek-v4-pro" />
            </label>
            {pulledModels.length > 0 && (
              <div>
                <p className="more-field-hint" style={{ margin: '0 0 4px' }}>Click a pulled model to fill the field above:</p>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                  {pulledModels.slice(0, 12).map(m => (
                    <button key={m} type="button" className={`more-btn small ${m === newModel ? 'primary' : ''}`} onClick={() => setNewModel(m)}>{m}</button>
                  ))}
                </div>
              </div>
            )}
            <div className="more-form-actions">
              <button type="button" className="more-btn primary" onClick={saveProvider} disabled={busy}>
                {editingId ? 'Save changes' : 'Add provider'}
              </button>
              {editingId && (
                <>
                  <button type="button" className="more-btn" onClick={() => testConnection(newBaseUrl, newApiKey)} disabled={busy}>Test connection</button>
                  <button type="button" className="more-btn" onClick={() => pullModels(newBaseUrl, newApiKey)} disabled={busy}>Pull models</button>
                </>
              )}
              <button type="button" className="more-btn" onClick={() => { setShowNewForm(false); setEditingId(null); setConnStatus(null); setPulledModels([]) }}>Cancel</button>
            </div>
          </div>
        )}
      </section>

      {/* System section */}
      <section className="more-section" aria-label="System">
        <div className="more-section-heading"><h3>System</h3></div>
        <p className="more-section-desc">
          Active: <strong>{config?.active ?? 'codex-cli'}</strong>{config?.activeModel ? ` · ${config.activeModel}` : ''}
          {config?.aiEnabled ? '' : ' · AI disabled'}.
          Provider config stored in <code>data/.ai-providers.json</code>.
          API keys stay local. Restart the server after switching providers.
        </p>
      </section>
    </section>
  )
}
