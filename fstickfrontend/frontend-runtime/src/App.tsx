import { useState, useEffect } from 'react'
import { runDsl, type DslRunResult } from './dsl/runner'
import { DslRenderer } from './dsl/Renderer'
import { startSyncLoop } from './dsl/backend'
import './App.css'

import libCode from './dsl/index.js?raw'
import appCode from './dsl/example-app.js?raw'

const INITIAL_STATE = {
  field1: '',
};

export default function App() {
  const [result, setResult] = useState<DslRunResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    try {
      const r = runDsl(libCode, appCode, { initialState: INITIAL_STATE })
      setResult(r)

      // Запускаем long-poll синхронизацию состояния с сервером.
      // При каждом сообщении: если state != null — применяем к store;
      // track_id позволяет откатить оптимистичные изменения конкретной команды.
      const stopSync = startSyncLoop((msg) => {
        if (msg.state) {
          r.backend.resolveTrace(msg.track_id ?? '', true, msg.state)
        }
      })

      return stopSync
    } catch (e) {
      setError(String(e))
    }
  }, [])

  if (error) return <pre style={{ color: 'red' }}>[DSL Error] {error}</pre>
  if (!result) return <div>Loading DSL…</div>

  return (
    <div style={{ padding: '2rem', maxWidth: 600, margin: '0 auto' }}>
      <DslRenderer node={result.tree} />
    </div>
  )
}
