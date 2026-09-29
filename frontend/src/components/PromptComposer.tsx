import type { FormEvent } from "react";
export function PromptComposer({
  prompt,
  onChange,
  onSubmit,
  busy,
  error,
}: {
  prompt: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  busy: boolean;
  error: string;
}) {
  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit();
  }
  return (
    <form className="composer" onSubmit={submit}>
      <label htmlFor="prompt">
        FİKRİNLE BAŞLA <span>01 / CREATE</span>
      </label>
      <textarea
        id="prompt"
        value={prompt}
        onChange={(e) => onChange(e.target.value)}
        disabled={busy}
        maxLength={10000}
        placeholder="Proje fikrini burada anlat..."
        aria-describedby={error ? "prompt-error" : "prompt-help"}
        aria-invalid={!!error}
        onKeyDown={(e) => {
          if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
            e.preventDefault();
            if (!busy) onSubmit();
          }
        }}
      />
      <p id="prompt-help">
        Örn: kategori ve grafiklerle bir harcama takip uygulaması oluştur.
      </p>
      <div className="composer-bottom">
        <span>
          <i className="online-dot" />{" "}
          {busy ? "Can üzerinde çalışıyor" : "Fikrinden ilk dosyaya"}
          <small>⌘ / Ctrl + Enter</small>
        </span>
        <button className="primary" disabled={busy} type="submit">
          {busy ? "Oluşturuluyor…" : "Projeyi Oluştur"}{" "}
          <span>{busy ? "◌" : "→"}</span>
        </button>
      </div>
      {error && (
        <div className="error" role="alert" id="prompt-error">
          {error}
        </div>
      )}
    </form>
  );
}
