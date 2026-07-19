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

interface BiteSource {
  type: string; id: string; title: string; area: string; summary: string; hasBiteCards: boolean
}
interface BiteCard {
  id: number; sourceType: string; sourceId: string; title: string; prompt: string; answer: string; hint: string; status: string
}

function apiErrorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error)
  if (message.includes('HTTP 502') || message.toLowerCase().includes('failed to fetch')) {
    return 'Backend API is not reachable. Start Learning OS backend first, then refresh this page.'
  }
  return message || 'Request failed'
}

export function MorePanel() {
  const [activeTab, setActiveTab] = useState<'providers' | 'bite'>('providers')
  const [config, setConfig] = useState<AiConfigResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // Daily Bite state
  const [biteSources, setBiteSources] = useState<BiteSource[]>([])
  const [biteCards, setBiteCards] = useState<BiteCard[]>([])
  const [extractedCards, setExtractedCards] = useState<BiteCard[]>([])
  const [selectedSource, setSelectedSource] = useState('')
  const [biteNotice, setBiteNotice] = useState('')
  const [biteBusy, setBiteBusy] = useState(false)

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
      setError(apiErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchConfig() }, [])

  // ---- Daily Bite helpers ----
  const fetchBiteData = async () => {
    try {
      const [srcRes, cardRes] = await Promise.all([
        fetch('/api/bite/sources'), fetch('/api/bites?status=active')
      ])
      if (srcRes.ok) setBiteSources((await srcRes.json()).sources || [])
      if (cardRes.ok) setBiteCards((await cardRes.json()).bites || [])
      if (!srcRes.ok || !cardRes.ok) setBiteNotice(apiErrorMessage(new Error(`HTTP ${srcRes.ok ? cardRes.status : srcRes.status}`)))
    } catch (e) {
      setBiteNotice(apiErrorMessage(e))
    }
  }
  useEffect(() => { if (activeTab === 'bite') fetchBiteData() }, [activeTab])

  const handleExtract = async (sourceId: string) => {
    setBiteBusy(true); setBiteNotice('')
    setSelectedSource(sourceId)
    const source = biteSources.find(s => s.id === sourceId)
    if (!source) { setBiteBusy(false); return }
    try {
      const res = await fetch(`/api/bite/extract?source_type=${source.type}&source_id=${encodeURIComponent(sourceId)}`)
      const data = await res.json()
      setExtractedCards(data.cards || [])
      setBiteNotice(`Extracted ${data.count} cards from "${source.title}". Review and save below.`)
    } catch (e) {
      setBiteNotice('Extraction failed: ' + (e instanceof Error ? e.message : String(e)))
    } finally { setBiteBusy(false) }
  }

  const handleSaveAll = async () => {
    setBiteBusy(true); setBiteNotice('')
    const source = biteSources.find(s => s.id === selectedSource)
    if (!source) { setBiteBusy(false); return }
    try {
      const res = await fetch(`/api/bite/extract-and-save?source_type=${source.type}&source_id=${encodeURIComponent(selectedSource)}`, { method: 'POST' })
      const data = await res.json()
      setBiteNotice(`Saved ${data.count} cards.`)
      setExtractedCards([]); setSelectedSource('')
      fetchBiteData()
    } catch (e) {
      setBiteNotice('Save failed: ' + (e instanceof Error ? e.message : String(e)))
    } finally { setBiteBusy(false) }
  }

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

      {/* Tab bar */}
      <nav className="more-tabs" style={{ display: 'flex', gap: 0, marginBottom: 18, borderBottom: '1px solid var(--line)' }}>
        <button style={{ padding: '8px 16px', border: 'none', borderBottom: activeTab === 'providers' ? '2px solid var(--accent)' : '2px solid transparent', background: 'none', cursor: 'pointer', fontWeight: activeTab === 'providers' ? 600 : 400, color: 'var(--ink)', fontSize: 14 }}
          onClick={() => setActiveTab('providers')}>AI Providers</button>
        <button style={{ padding: '8px 16px', border: 'none', borderBottom: activeTab === 'bite' ? '2px solid var(--accent)' : '2px solid transparent', background: 'none', cursor: 'pointer', fontWeight: activeTab === 'bite' ? 600 : 400, color: 'var(--ink)', fontSize: 14 }}
          onClick={() => setActiveTab('bite')}>Daily Bite</button>
      </nav>

      {activeTab === 'bite' && (
        <section className="more-section" aria-label="Daily Bite">
          <h3>Daily Bite Builder</h3>
          <p className="more-section-desc">Extract review cards from quiz Markdown and sync to mobile for review.</p>
          <h4 style={{ margin: '12px 0 6px' }}>Quiz Sources</h4>
          {biteSources.length === 0 ? <p className="more-field-hint">Loading…</p> : (
            <ul style={{ listStyle: 'none', padding: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
              {biteSources.filter(s => s.type === 'quiz').slice(0, 40).map(s => (
                <li key={s.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, padding: '8px 10px', borderRadius: 8, border: '1px solid var(--line)', background: 'var(--surface-card)' }}>
                  <span style={{ minWidth: 0 }}><strong style={{ fontSize: 13 }}>{s.title}</strong><span className="more-field-hint" style={{ display: 'block', fontSize: 11 }}>{s.area}{s.hasBiteCards ? ' · has cards' : ''}</span></span>
                  <button type="button" className="more-btn small primary" disabled={biteBusy} onClick={() => handleExtract(s.id)}>Extract</button>
                </li>
              ))}
            </ul>
          )}
          {extractedCards.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <h4 style={{ margin: 0 }}>Preview ({extractedCards.length})</h4>
                <button type="button" className="more-btn primary" onClick={handleSaveAll} disabled={biteBusy}>Save All</button>
              </div>
              {extractedCards.map((card, i) => (
                <div key={i} style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid var(--line)', marginBottom: 6, fontSize: 12 }}>
                  <p style={{ margin: '0 0 4px' }}><strong>Q:</strong> {card.prompt.slice(0, 150)}{card.prompt.length > 150 ? '…' : ''}</p>
                  <p style={{ margin: 0, color: 'var(--muted)' }}><strong>A:</strong> {card.answer.slice(0, 100)}{card.answer.length > 100 ? '…' : ''}</p>
                </div>
              ))}
            </div>
          )}
          {biteCards.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <h4 style={{ margin: '12px 0 6px' }}>Saved Cards ({biteCards.length})</h4>
              <ul style={{ listStyle: 'none', padding: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
                {biteCards.slice(0, 30).map(c => (
                  <li key={c.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '6px 8px', borderRadius: 6, border: '1px solid var(--line)', fontSize: 12 }}>
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.title}: {c.prompt.slice(0, 50)}…</span>
                    <span className="more-badge">{c.status}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {biteNotice && <p className="more-field-hint" style={{ marginTop: 8 }}>{biteNotice}</p>}
        </section>
      )}

      {activeTab === 'providers' && (
      <>
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
      </>
      )}
    </section>
  )
}
