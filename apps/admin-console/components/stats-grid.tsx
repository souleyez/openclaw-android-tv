type StatItem = {
  label: string;
  value: string | number;
  hint: string;
  tone?: "default" | "accent" | "warn";
};

type StatsGridProps = {
  items: StatItem[];
};

export function StatsGrid({ items }: StatsGridProps) {
  return (
    <section className="stats-grid">
      {items.map((item) => (
        <article key={item.label} className={`panel stat-card stat-card--${item.tone ?? "default"}`}>
          <p className="stat-card__label">{item.label}</p>
          <strong className="stat-card__value">{item.value}</strong>
          <span className="stat-card__hint">{item.hint}</span>
        </article>
      ))}
    </section>
  );
}
