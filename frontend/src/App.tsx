import { useEffect, useState, useRef } from "react";
import { Sidebar } from "./components/Sidebar";
import type { Page } from "./components/Sidebar";
import { Hero } from "./components/Hero";
import { suggestions } from "./types/suggestions";
import { PromptComposer } from "./components/PromptComposer";
import { AgentPanel } from "./components/AgentPanel";
import { DeveloperPanel } from "./components/DeveloperPanel";
import { getGenerationMode } from "./services/agentApi";
import type {
  AgentResponse,
  TerminalEvent,
  GenerationMode,
} from "./types/agent";
import { executionEvents, modeLabel } from "./services/execution";
import { runExecution, liveSteps, terminalEvent, executionResult } from "./services/executionApi";
import type { Progress, ExecutionSnapshot } from "./services/executionApi";
import "./App.css";
function event(message: string, error = false): TerminalEvent {
  return { time: new Date().toLocaleTimeString("tr-TR"), message, error };
}
function App() {
  const [live, setLive] = useState<Progress[]>([]);
  const [execution, setExecution] = useState<ExecutionSnapshot | null>(null);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);
  const [mode, setMode] = useState<GenerationMode | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    getGenerationMode(controller.signal)
      .then(setMode)
      .catch(() => setMode(null));
    return () => {
      clearTimeout(timeout);
      controller.abort();
    };
  }, []);
  const [page, setPage] = useState<Page>("Ana Sayfa");
  const [prompt, setPrompt] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<AgentResponse | null>(null);
  const [runs, setRuns] = useState<Record<string, ExecutionSnapshot>>({});
  const [history, setHistory] = useState<AgentResponse[]>([]);
  const [events, setEvents] = useState<TerminalEvent[]>(() => [
    event("Clinecan çalışma alanı açıldı."),
  ]);
  function suggest(text: string) {
    setPrompt(text);
    setError("");
    setPage("Ana Sayfa");
    setTimeout(() => document.getElementById("prompt")?.focus(), 0);
  }
  function reset() {
    if (busy) return;
    setPrompt("");
    setResult(null);
    setExecution(null); setLive([]);
    setError("");
    setPage("Ana Sayfa");
    setEvents([event("Yeni proje başlatıldı.")]);
  }
  function selectProject(r: AgentResponse) {
    if (busy) return;
    setResult(r);
    setExecution(runs[r.execution.id] ?? null); setLive(runs[r.execution.id]?.events ?? []);
    setPrompt(r.originalPrompt);
    setError(
      r.status === "FAILED"
        ? "Bu proje doğrulanamadı. Agent adımlarını incele."
        : "",
    );
    setPage("Ana Sayfa");
    setEvents([
      event(
        "Bu oturumdaki proje açıldı. Aşağıdaki adımlar kayıtlı sonuçtan alındı.",
      ),
      ...executionEvents(r),
    ]);
  }
  async function submit() {
    if (busy) return;
    if (!prompt.trim()) {
      setError("Bir fikre ihtiyacımız var. Projeni birkaç kelimeyle anlat.");
      return;
    }
    setBusy(true);
    setError("");
    setResult(null);
    setExecution(null); setLive([]);
    setEvents([event("İstek backend’e gönderildi. Sonuç bekleniyor.")]);
    try {
      active.current = new AbortController();
      const snapshot = await runExecution(prompt.trim(), progress => {
        setLive(prev => [...prev, progress]);
        setEvents(prev => [...prev, terminalEvent(progress)]);
      }, { signal: active.current.signal });
      setExecution(snapshot);
      setRuns(prev => Object.fromEntries([...Object.entries(prev), [snapshot.id, snapshot]].slice(-10)));
      const data = executionResult(snapshot);
      if (snapshot.error) setError(snapshot.error.message);
      if (!data) return;
      setResult(data);
      setHistory((prev) => [data, ...prev].slice(0, 10));
      setMode(data.generationMode);

      if (data.status === "FAILED")
        setError(data.error?.message ?? "Proje oluşturulamadı.");
    } catch (e) {
      const message =
        e instanceof Error ? e.message : "Beklenmeyen bir hata oluştu.";
      setError(message);
      setEvents((prev) => [...prev, event(message, true)]);
    } finally {
      active.current = null;
      setBusy(false);
    }
  }
  return (
    <div className="app-shell">
      <Sidebar
        page={page}
        onPage={setPage}
        onNew={reset}
        history={history}
        busy={busy}
        onSelect={selectProject}
      />
      <div className="main-shell">
        <header className="topbar">
          <div>
            Çalışma alanı <span>/</span> <strong>{page}</strong>
          </div>
          <div>
            <span
              className="version"
              data-testid="generation-mode"
              role="status"
            >
              {modeLabel(mode, result)}
            </span>
            <span className="top-avatar">C</span>
          </div>
        </header>
        <div className="workspace">
          <main className="center-workspace">
            {page === "Ana Sayfa" ? (
              <>
                <Hero onSuggest={suggest} busy={busy} />
                <PromptComposer
                  prompt={prompt}
                  onChange={setPrompt}
                  onSubmit={submit}
                  busy={busy}
                  error={error}
                />
                {busy && <button className="cancel-execution" onClick={() => active.current?.abort()}>İşlemi iptal et</button>}
                <AgentPanel result={result} busy={busy} failed={!!error} steps={live.length ? liveSteps(live) : undefined} build={execution?.build} />
              </>
            ) : (
              <section className="utility-page">
                <span className="eyebrow">CLINECAN WORKSPACE</span>
                <h1>{page}</h1>
                {page === "Şablonlar" ? (
                  <>
                    <p>Bir başlangıç fikri seç, kendi detaylarını ekle.</p>
                    {suggestions.map((s) => (
                      <button
                        disabled={busy}
                        key={s}
                        onClick={() => suggest(s)}
                      >
                        {s} →
                      </button>
                    ))}
                  </>
                ) : page === "Projelerim" ? (
                  <>
                    <p>
                      Bu oturumda oluşturulan projeler. Sayfa yenilendiğinde
                      geçmiş temizlenir.
                    </p>
                    {history.length ? (
                      history.map((r, i) => (
                        <button
                          key={i}
                          disabled={busy}
                          onClick={() => selectProject(r)}
                        >
                          {r.originalPrompt}{" "}
                          <small>{r.files.length} dosya</small>
                        </button>
                      ))
                    ) : (
                      <button disabled={busy} onClick={reset}>
                        İlk projeni oluştur →
                      </button>
                    )}
                  </>
                ) : (
                  <>
                    <p><strong>Bağlantı Durumu</strong></p>
                    <p>● Clinecan Backend · Bağlı</p>

                    <p><strong>Çalışma Modu</strong></p>
                    <p>{modeLabel(mode, result)}</p>
                    <p>Güvenli ve deterministik proje üretimi aktif.</p>

                    <p><strong>Altyapı</strong></p>
                    <p>Railway Cloud</p>
                  </>
                )}
              </section>
            )}
            <footer className="main-footer">
              FİKİRDEN KODA, BİR KAHVE MOLASINDA.<span>Clinecan © 2026</span>
            </footer>
          </main>
          <DeveloperPanel
            key={history.indexOf(result!)}
            result={result}
            events={events}
            execution={execution}
          />
        </div>
      </div>
    </div>
  );
}
export default App;
