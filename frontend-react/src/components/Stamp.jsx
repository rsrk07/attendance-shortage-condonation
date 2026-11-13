import { formatDate } from "../lib/format.js";

/**
 * An office approval stamp: double-ruled ink box, slightly rotated.
 * Stamps carry capital lettering the way real rubber stamps do.
 */
export default function Stamp({ approved, title, name, date, size = "md", rotate = -4 }) {
  const ink = approved ? "text-approve" : "text-reject";
  const text = size === "lg" ? "text-3xl px-5 py-2" : "text-lg px-3 py-1";
  return (
    <div
      className={`inline-block select-none rounded-[3px] border-[3px] border-current text-center outline-1 outline-offset-2 outline-current ${ink} ${text}`}
      style={{ transform: `rotate(${rotate}deg)`, opacity: 0.88 }}
      aria-label={`${title}${name ? ` by ${name}` : ""}${date ? ` on ${formatDate(date)}` : ""}`}
    >
      <p className="font-display font-bold uppercase leading-none tracking-[0.12em]">{title}</p>
      {(name || date) && (
        <p className="mt-0.5 font-display text-xs font-semibold uppercase tracking-wider">
          {name}
          {name && date ? ", " : ""}
          {date ? formatDate(date) : ""}
        </p>
      )}
    </div>
  );
}
