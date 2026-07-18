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

  const saveProvider = async () => {
    const id = editingId || newLabel.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '')
    if (!id) { setNotice('Enter a label to generate a provider id.'); return }

    try {
      const res = await fetch('/api/system/ai-config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id, label: newLabel || id, apiKey: newApiKey, baseUrl: newBaseUrl, model: newModel,
        }),
      })
      if (!res.ok) { const d = await res.json(); throw new Error((d as any).detail || 'Save failed') }
      setShowNewForm(false)
      setEditingId(null)
      setNewLabel(''); setNewApiKey(''); setNewBaseUrl('https://api.deepseek.com/v1'); setNewModel('')
      setNotice('Provider saved.')
      fetchConfig()
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Save failed')
    }
  }

  const activateProvider = async (id: string) => {
    try {
      const res = await fetch('/api/system/ai-config/activate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id }),
      })
      if (!res.ok) { const d = await res.json(); throw new Error((d as any).detail || 'Activation failed') }
      setNotice(`Activated '${id}'. Restart server for full effect.`)
      fetchConfig()
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Activation failed')
    }
  }

  const deleteProvider = async (id: string) => {
    if (!confirm(`Remove provider '${id}'?`)) return
    try {
      await fetch(`/api/system/ai-config/${encodeURIComponent(id)}`, { method: 'DELETE' })
      setNotice('Provider removed.')
      fetchConfig()
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Delete failed')
    }
  }

  const editProvider = (p: AiProvider) => {
    setEditingId(p.id)
    setNewLabel(p.label)
    setNewApiKey(p.apiKey)
    setNewBaseUrl(p.baseUrl)
    setNewModel(p.model)
    setShowNewForm(true)
  }

  if (loading) return <section className="more-panel"><p className="detail-loading">Loading…</p></section>
  if (error) return <section className="more-panel"><p className="error-banner">{error}</p></section>

  return (
    <section className="more-panel" aria-label="More settings">
      <header>
        <h2>More</h2>
        <p>Configure AI providers, system settings, and tools.</p>
      </header>

      {notice && (
        <p className="inline-hint" role="status" style={{ marginBottom: 12 }}>
          {notice}
          <button type="button" onClick={() => setNotice('')} style={{ marginLeft: 8, cursor: 'pointer', background: 'none', border: 'none', color: 'var(--accent)' }}>Dismiss</button>
        </p>
      )}

      {/* AI Provider Section */}
      <section className="more-section" aria-label="AI providers">
        <div className="more-section-heading">
          <h3>AI Provider</h3>
          <span className="more-section-badge">
            {config?.active ?? 'codex-cli'}
          </span>
        </div>
        <p className="more-section-desc">
          Manage AI service providers. The active provider is used for assistant answers,
          draft generation, and content review. OpenAI-compatible APIs (DeepSeek, Kimi, etc.) are supported.
        </p>

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
                    {p.provider === 'codex-cli' ? 'Codex CLI' : p.baseUrl || 'OpenAI compatible'} · {p.model || 'model not set'}
                  </span>
                </div>
                <div className="more-provider-actions">
                  {!p.isBuiltIn && (
                    <button type="button" className="more-btn small" onClick={() => editProvider(p)}>Edit</button>
                  )}
                  {!isActive && (
                    <button type="button" className="more-btn small primary" onClick={() => activateProvider(p.id)}>
                      Activate
                    </button>
                  )}
                  {!p.isBuiltIn && (
                    <button type="button" className="more-btn small danger" onClick={() => deleteProvider(p.id)}>Remove</button>
                  )}
                </div>
              </li>
            )
          })}
        </ul>

        {!showNewForm && (
          <button type="button" className="more-btn" onClick={() => { setEditingId(null); setNewLabel(''); setNewApiKey(''); setNewBaseUrl('https://api.deepseek.com/v1'); setNewModel(''); setShowNewForm(true) }}>
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
              <span className="more-field-hint">OpenAI-compatible endpoint. Leave trailing /v1.</span>
            </label>
            <label>
              <span>Model</span>
              <input value={newModel} onChange={e => setNewModel(e.target.value)} placeholder="deepseek-v4-flash or deepseek-v4-pro" />
            </label>
            <div className="more-form-actions">
              <button type="button" className="more-btn primary" onClick={saveProvider}>
                {editingId ? 'Save changes' : 'Add provider'}
              </button>
              <button type="button" className="more-btn" onClick={() => { setShowNewForm(false); setEditingId(null) }}>
                Cancel
              </button>
            </div>
          </div>
        )}
      </section>

      {/* System section */}
      <section className="more-section" aria-label="System">
        <div className="more-section-heading">
          <h3>System</h3>
        </div>
        <p className="more-section-desc">
          Current AI: <strong>{config?.active ?? 'codex-cli'}</strong>
          {config?.activeModel && ` · ${config.activeModel}`}
          {config?.aiEnabled ? '' : ' · AI disabled'}
        </p>
        <p className="more-section-desc">
          Adding a provider writes config to <code>data/.ai-providers.json</code>.
          API keys are stored locally and never leave your machine.
          After switching providers, click the Restart button on the Home Dashboard.
        </p>
      </section>
    </section>
  )
}
