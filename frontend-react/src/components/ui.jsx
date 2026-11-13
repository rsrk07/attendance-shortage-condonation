import { STATUS } from "../lib/format.js";

const TONES = {
  pending: "text-pending",
  action: "text-register",
  approve: "text-approve",
  reject: "text-reject",
};

export function StatusBadge({ status }) {
  const s = STATUS[status] || { label: status, tone: "pending" };
  return (
    <span className={`inline-flex items-center gap-1.5 text-sm font-semibold ${TONES[s.tone]}`}>
      <span className="h-2 w-2 rounded-full bg-current" aria-hidden="true" />
      {s.label}
    </span>
  );
}

export function PageHeader({ title, children, description }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-4xl">{title}</h1>
        {description && <p className="mt-1 max-w-2xl text-ink-soft">{description}</p>}
      </div>
      {children && <div className="flex flex-wrap items-center gap-3">{children}</div>}
    </div>
  );
}

const NOTICE_TONES = {
  info: "border-register/30 bg-register/5 text-ink",
  success: "border-approve/30 bg-approve/5 text-ink",
  error: "border-reject/30 bg-reject/5 text-ink",
  warning: "border-pending/30 bg-highlight text-ink",
};

export function Notice({ tone = "info", title, children, className = "" }) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={`rounded-md border px-4 py-3 ${NOTICE_TONES[tone]} ${className}`}
    >
      {title && <p className="font-semibold">{title}</p>}
      {children && <div className={title ? "mt-1 text-sm" : "text-sm"}>{children}</div>}
    </div>
  );
}

export function Loading({ label = "Loading" }) {
  return (
    <p className="py-10 text-ink-soft" role="status">
      {label}…
    </p>
  );
}

export function ErrorNotice({ error, onRetry }) {
  if (!error) return null;
  return (
    <Notice tone="error" title="Something went wrong">
      <p>{error.message}</p>
      {onRetry && (
        <button type="button" className="link mt-2" onClick={onRetry}>
          Try again
        </button>
      )}
    </Notice>
  );
}

/** Label/value pairs for request facts. */
export function Facts({ items }) {
  return (
    <dl className="grid grid-cols-[minmax(8rem,auto)_1fr] gap-x-6 gap-y-2 text-sm">
      {items
        .filter((item) => item && item.value !== undefined)
        .map((item) => (
          <div key={item.label} className="contents">
            <dt className="text-ink-soft">{item.label}</dt>
            <dd className="font-medium">{item.value ?? "–"}</dd>
          </div>
        ))}
    </dl>
  );
}

export function FieldError({ id, message }) {
  if (!message) return null;
  return (
    <p id={id} className="mt-1 text-sm text-reject">
      {message}
    </p>
  );
}
