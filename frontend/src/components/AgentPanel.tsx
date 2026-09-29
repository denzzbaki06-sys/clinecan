import type { AgentResponse, AgentStep } from "../types/agent";
const initial: AgentStep[] = [
  {
    name: "ANALYZE",
    status: "IDLE",
    message: "Fikrini ve gereksinimlerini anlıyor...",
  },
  {
    name: "PLAN",
    status: "IDLE",
    message: "Proje yapısını ve mimariyi planlıyor...",
  },
  {
    name: "GENERATE",
    status: "IDLE",
    message: "React ve TypeScript dosyalarını oluşturuyor...",
  },
  {
    name: "VALIDATE",
    status: "IDLE",
    message: "Dosya yollarını ve içeriği statik olarak doğruluyor...",
  },
  { name: "BUILD", status: "IDLE", message: "İzole ortamda TypeScript ve Vite derlemesi..." },
];
export function AgentStepItem({
  step,
  index,
}: {
  step: AgentStep;
  index: number;
}) {
  return (
    <li className={`agent-step ${step.status.toLowerCase()}`}>
      <span className="step-indicator">
        {step.status === "COMPLETED"
          ? "✓"
          : step.status === "FAILED"
            ? "!"
            : step.status === "RUNNING"
              ? "◌"
              : index + 1}
      </span>
      <div>
        <strong>{step.name}</strong>
        <p>{step.message}</p>
        {step.durationMs !== undefined && step.status !== "PENDING" && (
          <span className="step-duration">{step.durationMs} ms</span>
        )}
      </div>
      <small>
        {
          {
            IDLE: "Bekliyor",
            PENDING: "Bekliyor",
            RUNNING: "Çalışıyor",
            COMPLETED: "Tamamlandı",
            FAILED: "Başarısız",
          }[step.status]
        }
      </small>
    </li>
  );
}
export function AgentPanel({
  result,
  busy,
  failed,
  steps,
  build,
}: {
  result: AgentResponse | null;
  busy: boolean;
  failed: boolean;
  steps?: AgentStep[];
  build?: {success: boolean} | null;
}) {
  return (
    <section className="agent-panel glass">
      <header>
        <span className="agent-icon">⌘</span>
        <div>
          <h2>Clinecan Agent</h2>
          <p>Projeni analiz ediyor, planlıyor, kodluyor ve doğruluyor...</p>
        </div>
        <span className={`status-badge ${busy ? "pulsing" : ""}`}>
          {busy
            ? "Çalışıyor"
            : failed || result?.status === "FAILED"
              ? "Hata"
              : result
                ? "Tamamlandı"
                : "Hazır"}
        </span>
      </header>
      {busy && (
        <p className="pending" role="status">
          <span className="spinner" /> Can çalışıyor. Aşamalar canlı olarak güncelleniyor.
        </p>
      )}
      {result?.summary && (
        <div className="project-summary">
          <strong>{result.projectName}</strong>
          <p>{result.summary}</p>
        </div>
      )}
      {result?.generationMode === "DEMO" && (
        <p className="demo-note">
          Demo Mode · Şablon tabanlı üretim. LLM çağrısı yapılmadı.
        </p>
      )}
      <ol>
        {(steps ?? result?.steps ?? initial).map((step, i) => (
          <AgentStepItem key={`${step.name}-${i}`} step={step} index={i} />
        ))}
      </ol>
      <footer>
        <span>◇</span>{" "}
        {build?.success ? "Can halletti. ☕️ İzole derleme başarılı." : build ? "İzole derleme başarısız." : result
          ? "Yapısal doğrulama • derleme sonucu yok"
          : "Fikrin sende. İlk adım Can’da."}
        <span>POWERED BY CLINECAN</span>
      </footer>
    </section>
  );
}
