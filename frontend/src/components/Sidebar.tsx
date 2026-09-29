import type { AgentResponse } from "../types/agent";
export type Page = "Ana Sayfa" | "Projelerim" | "Şablonlar" | "Ayarlar";
export function Sidebar({
  page,
  onPage,
  onNew,
  history,
  onSelect,
  busy,
}: {
  page: Page;
  onPage: (p: Page) => void;
  onNew: () => void;
  history: AgentResponse[];
  onSelect: (r: AgentResponse) => void;
  busy: boolean;
}) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-row">
          <span className="brand-mark">
            c<span>›</span>
          </span>
          <strong>
            Clinecan<span className="brand-dot">.</span>
          </strong>
        </div>
        <p>Sen söyle, Can kodlasın. ☕️</p>
        <small>Autonomous AI Coding Agent</small>
      </div>
      <button className="new-project" onClick={onNew} disabled={busy}>
        <span>＋</span> Yeni Proje <kbd>↗</kbd>
      </button>
      <nav aria-label="Ana menü">
        {(["Ana Sayfa", "Projelerim", "Şablonlar", "Ayarlar"] as Page[]).map(
          (p, i) => (
            <button
              key={p}
              onClick={() => onPage(p)}
              className={page === p ? "nav-item active" : "nav-item"}
            >
              <span aria-hidden="true">{["⌂", "▦", "◈", "⚙"][i]}</span>
              {p}
              {p === page && <i />}
            </button>
          ),
        )}
      </nav>
      <div className="recent">
        <div className="eyebrow">
          SON PROJELER <span>{history.length.toString().padStart(2, "0")}</span>
        </div>
        {history.length ? (
          history.map((r, i) => (
            <button
              key={i}
              disabled={busy}
              onClick={() => onSelect(r)}
              title={r.originalPrompt}
            >
              <span>⌁</span>
              {r.originalPrompt.slice(0, 27)}
            </button>
          ))
        ) : (
          <p>
            İlk fikrin burada yerini alsın.
            <br />
            <span>Bir kahve, yeni bir başlangıç.</span>
          </p>
        )}
      </div>
      <div className="pro-card">
        <span className="pro-label">BİR SEVİYE DAHA İLERİ</span>
        <h3>
          Clinecan <em>Pro</em>
          <span>✦</span>
        </h3>
        <p>
          Büyük fikirler için
          <br />
          biraz daha fazla kahve.
        </p>
        <div>
          Çok yakında <span>↗</span>
        </div>
      </div>
      <div className="sidebar-footer">
        <span className="avatar">D</span>
        <div>
          Geliştirici alanı<small>Yerel çalışma alanı</small>
        </div>
        <span className="online-dot" />
      </div>
    </aside>
  );
}
