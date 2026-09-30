import { artifactUrl, previewUrl } from "../services/executionApi";
import type { ExecutionSnapshot } from "../services/executionApi";
import { safePreviewProvider } from "../services/previewProvider";
import { useState } from "react";
import type {
  AgentResponse,
  GeneratedFile,
  TerminalEvent,
} from "../types/agent";
export function FileExplorer({
  files,
  selected,
  onSelect,
}: {
  files: GeneratedFile[];
  selected: string;
  onSelect: (path: string) => void;
}) {
  const folders = [
    ...new Set(
      files.map((f) =>
        f.path.includes("/")
          ? f.path.substring(0, f.path.lastIndexOf("/"))
          : "",
      ),
    ),
  ].sort();
  return (
    <div className="file-explorer">
      {folders.map((folder) => (
        <div key={folder}>
          {folder && (
            <div className="folder">
              ⌄ <span>▱</span> {folder}/
            </div>
          )}
          {files
            .filter(
              (f) =>
                (f.path.includes("/")
                  ? f.path.substring(0, f.path.lastIndexOf("/"))
                  : "") === folder,
            )
            .map((f) => (
              <button
                key={f.path}
                className={selected === f.path ? "selected" : ""}
                onClick={() => onSelect(f.path)}
                style={{ paddingLeft: folder ? 35 : 20 }}
              >
                <span
                  className={`file-icon ${f.path.endsWith(".css") ? "css" : ""}`}
                >
                  {f.path.endsWith(".tsx")
                    ? "TS"
                    : f.path.endsWith(".css")
                      ? "#"
                      : "{}"}
                </span>
                {f.path.split("/").pop()}
              </button>
            ))}
        </div>
      ))}
    </div>
  );
}
function colorLine(line: string) {
  return line
    .split(
      /('[^']*'|"[^"]*"|\b(?:import|from|export|default|function|return|const)\b)/g,
    )
    .map((part, i) => (
      <span
        key={i}
        className={
          /^["']/.test(part)
            ? "token-string"
            : /^(import|from|export|default|function|return|const)$/.test(part)
              ? "token-keyword"
              : ""
        }
      >
        {part}
      </span>
    ));
}
export function CodeViewer({ file }: { file?: GeneratedFile }) {
  if (!file)
    return (
      <div className="editor-empty">
        <span>⌘</span>
        <h3>Bir fikir, binlerce olasılık.</h3>
        <p>
          Projen oluştuğunda dosyaların
          <br />
          burada seni bekliyor olacak.
        </p>
        <small>YOUR NEXT BUILD STARTS HERE</small>
      </div>
    );
  return (
    <div
      className="code-scroll"
      tabIndex={0}
      aria-label={`${file.path} içeriği`}
    >
      <pre>
        {file.content.split("\n").map((line, i) => (
          <div className="code-line" key={i}>
            <span className="line-number" aria-hidden="true">
              {i + 1}
            </span>
            <code>{colorLine(line) || " "}</code>
          </div>
        ))}
      </pre>
    </div>
  );
}
// Integration boundary for a future isolated sandbox runtime. Never executes generated code.
export function PreviewPanel({ result, execution }: { result: AgentResponse | null; execution?: ExecutionSnapshot | null }) {
  const download = artifactUrl(execution ?? null);
  const livePreview = previewUrl(execution ?? null);
  const preview = result ? safePreviewProvider.describe(result) : null;
  const [viewport, setViewport] = useState<"desktop" | "tablet" | "mobile">("desktop");
  const [refreshKey, setRefreshKey] = useState(0);
  const widths = { desktop: "100%", tablet: "768px", mobile: "390px" } as const;

  // Hosted DEMO completes generation/validation without a Docker runtime.
  // This is an expected deployment mode, not a project build failure.
  const hostedDemo =
    result?.generationMode === "DEMO" &&
    execution?.status === "COMPLETED" &&
    execution?.build?.success === false;

  if (!result) return (
    <div className="preview-panel">
      <div className="editor-empty"><span>▧</span><h3>Fikrine bir pencere aç.</h3><p>Başarılı derlemeden sonra çalışan uygulama burada açılacak.</p></div>
    </div>
  );

  return (
    <div className="preview-panel">
      <div className="preview-toolbar">
        <div className="preview-address">
          ◇ &nbsp; {livePreview ? "İzole canlı önizleme" : hostedDemo ? "Hosted Demo hazır" : "Önizleme bekleniyor"}
        </div>
        <div className="preview-controls" aria-label="Önizleme boyutu">
          {(["desktop", "tablet", "mobile"] as const).map(size => (
            <button key={size} className={viewport === size ? "active" : ""} onClick={() => setViewport(size)}>
              {size === "desktop" ? "Desktop" : size === "tablet" ? "Tablet" : "Mobile"}
            </button>
          ))}
          <button onClick={() => setRefreshKey(key => key + 1)} disabled={!livePreview}>↻ Yenile</button>
        </div>
      </div>

      {livePreview ? (
        <div className="preview-stage">
          <iframe
            key={`${livePreview}-${refreshKey}`}
            title={`${result.projectName} canlı önizleme`}
            src={livePreview}
            sandbox="allow-scripts"
            referrerPolicy="no-referrer"
            style={{ width: widths[viewport] }}
          />
        </div>
      ) : (
        <div className="preview-card">
          <small>
            {hostedDemo
              ? "HOSTED DEMO"
              : execution?.build?.success
                ? "PREVIEW HAZIRLANIYOR"
                : "BUILD REQUIRED"}
          </small>
          <h2>{preview?.title}</h2>
          <p>
            {hostedDemo
              ? "Kaynak üretimi ve doğrulama başarıyla tamamlandı. Canlı Docker önizlemesi yerel geliştirme ortamında kullanılabilir."
              : execution?.build?.success
                ? "Derleme başarılı ancak çalıştırılabilir preview URL'si henüz hazır değil."
                : "Çalışan önizleme yalnızca başarılı Docker derlemesinden sonra açılır."}
          </p>
          <span>{result.files.length} dosya oluşturuldu</span>
        </div>
      )}

      <div className="preview-actions">
        {download && <a className="preview-download" href={download} download="clinecan-build.zip">Projeyi İndir (.zip)</a>}
        {execution?.build && (
          <span className={execution.build.success || hostedDemo ? "preview-success" : "preview-failure"}>
            {execution.build.success
              ? `✓ Docker build başarılı · ${execution.repairAttempts} onarım`
              : hostedDemo
                ? "✓ Hosted Demo tamamlandı · kaynak doğrulaması başarılı"
                : "Derleme başarısız · executable preview yok"}
          </span>
        )}
      </div>
      <p className="preview-note">Preview ayrı, sandboxed bir iframe içinde çalışır. <code>allow-same-origin</code> verilmez; Clinecan uygulamasının oturumuna ve JavaScript bağlamına erişemez.</p>
    </div>
  );
}
export function TerminalPanel({ events }: { events: TerminalEvent[] }) {
  return (
    <div className="terminal" role="log" aria-label="Agent olayları">
      <p className="terminal-heading">CLINECAN / AGENT EVENTS</p>
      {events.map((event, i) => (
        <p key={i} className={event.error ? "terminal-error" : ""}>
          <time>{event.time}</time>
          <span>›</span>
          {event.message}
        </p>
      ))}
      <p className="terminal-note">
        Gerçek agent olayları gösterilir. Üretilen kod yalnızca izole sandbox içinde derlenir.
      </p>
      <span className="terminal-cursor">▌</span>
    </div>
  );
}
export function DeveloperPanel({
  result,
  events,
  execution,
}: {
  result: AgentResponse | null;
  events: TerminalEvent[];
  execution?: ExecutionSnapshot | null;
}) {
  const [tab, setTab] = useState("Code");
  const [selected, setSelected] = useState("");
  const [open, setOpen] = useState<string[]>([]);
  const files = result?.files ?? [];
  const active = files.find((f) => f.path === selected) ?? files[0];
  const tabs = [
    ...new Set([
      ...(active ? [active.path] : []),
      ...open.filter((path) => files.some((f) => f.path === path)),
    ]),
  ];
  function select(path: string) {
    setSelected(path);
    setOpen((prev) => [...new Set([...prev, path])]);
  }
  return (
    <aside className="developer-panel">
      <header className="developer-header">
        <span>⌘</span>
        <strong>Geliştirici Alanı</strong>
        <span className="workspace-tag">WORKSPACE</span>
      </header>
      <div
        className="developer-tabs"
        role="tablist"
        aria-label="Geliştirici panelleri"
      >
        {["Code", "Preview", "Terminal"].map((t, i) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            id={`tab-${t}`}
            aria-controls="developer-content"
            onClick={() => setTab(t)}
          >
            <span>{["〈/〉", "▧", ">_"][i]}</span>
            {t}
          </button>
        ))}
      </div>
      <div
        id="developer-content"
        role="tabpanel"
        aria-labelledby={`tab-${tab}`}
        className="developer-content"
      >
        {tab === "Code" ? (
          <>
            <div className="project-root">
              ⌄ &nbsp; ▱ &nbsp; {result?.projectName ?? "Yeni projen"}
              <span>{files.length} files</span>
            </div>
            {files.length > 0 && (
              <FileExplorer
                files={files}
                selected={active?.path ?? ""}
                onSelect={select}
              />
            )}
            <div className="editor-tabs">
              {tabs.length ? (
                tabs.map((path) => (
                  <button
                    key={path}
                    className={active?.path === path ? "active" : ""}
                    onClick={() => select(path)}
                  >
                    {path.split("/").pop()} <span>•</span>
                  </button>
                ))
              ) : (
                <span>Henüz açık dosya yok</span>
              )}
            </div>
            <CodeViewer file={active} />
          </>
        ) : tab === "Preview" ? (
          <PreviewPanel result={result} execution={execution} />
        ) : (
          <TerminalPanel events={events} />
        )}
      </div>
      <footer className="developer-footer">
        <span>
          <i className="online-dot" />{" "}
          {result ? `${files.length} dosya` : "Başlamaya hazır"}
        </span>
        <span>
          UTF-8 &nbsp;{" "}
          {active?.path.split(".").pop()?.toUpperCase() ?? "CLINECAN"}
        </span>
      </footer>
      <div className="dev-note">
        <span>☕</span>
        <p>
          Sen büyük düşün.
          <br />
          <strong>Can detayları halleder.</strong>
        </p>
      </div>
    </aside>
  );
}
