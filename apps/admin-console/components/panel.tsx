import { ReactNode } from "react";

type PanelProps = {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
};

export function Panel({ title, subtitle, actions, children }: PanelProps) {
  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h3 className="panel__title">{title}</h3>
          {subtitle ? <p className="panel__subtitle">{subtitle}</p> : null}
        </div>
        {actions ? <div>{actions}</div> : null}
      </div>
      {children}
    </section>
  );
}
