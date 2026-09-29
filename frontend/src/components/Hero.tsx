import { suggestions } from "../types/suggestions";
export function Hero({
  onSuggest,
  busy,
}: {
  onSuggest: (text: string) => void;
  busy: boolean;
}) {
  return (
    <section className="hero">
      <div className="hero-badge">
        <span>✦</span> Kod yazmak hiç bu kadar kolay olmamıştı.
      </div>
      <h1>
        Bugün
        <br />
        <em>ne kodluyoruz?</em>
      </h1>
      <h2>
        Sen söyle, Can kodlasın. <span>☕️</span>
      </h2>
      <p>
        Fikirlerini gerçeğe dönüştüren yapay zeka kod asistanı.
        <br />
        Clinecan analiz eder, planlar, kod yazar ve senin için doğrular.
      </p>
      <div className="suggestions">
        {suggestions.map((s, i) => (
          <button key={s} disabled={busy} onClick={() => onSuggest(s)}>
            <span>{["◷", "⌘", "☷", "◇"][i]}</span>
            {s}
            <span>↗</span>
          </button>
        ))}
      </div>
    </section>
  );
}
