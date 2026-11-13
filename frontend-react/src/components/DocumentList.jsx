import { useState } from "react";
import { downloadFile } from "../lib/api.js";
import { fileSize } from "../lib/format.js";

const VERIFICATION = {
  VERIFIED: { label: "Verified", className: "text-approve" },
  REJECTED: { label: "Rejected", className: "text-reject" },
  PENDING: { label: "Not checked yet", className: "text-ink-soft" },
};

/** Submitted documents with their automatic verification result and a download button. */
export default function DocumentList({ documents }) {
  const [error, setError] = useState(null);
  if (!documents?.length) return <p className="text-sm text-ink-soft">No documents.</p>;

  async function open(doc) {
    setError(null);
    try {
      await downloadFile(doc.fileId, doc.fileName);
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <div>
      <ul className="divide-y divide-rule">
        {documents.map((doc) => {
          const v = VERIFICATION[doc.verificationStatus] || VERIFICATION.PENDING;
          return (
            <li key={doc.id} className="flex flex-wrap items-start justify-between gap-3 py-3 first:pt-0 last:pb-0">
              <div className="min-w-0">
                <p className="font-medium">{doc.typeLabel}</p>
                <p className="truncate text-sm text-ink-soft">
                  {doc.fileName} ({fileSize(doc.sizeBytes)})
                  {doc.description ? `, ${doc.description}` : ""}
                </p>
                <p className={`text-sm ${v.className}`}>
                  {v.label}
                  {doc.verificationNote && doc.verificationStatus !== "PENDING" ? `: ${doc.verificationNote}` : ""}
                </p>
              </div>
              <button type="button" className="btn-quiet px-3 py-1 text-sm" onClick={() => open(doc)}>
                Download
              </button>
            </li>
          );
        })}
      </ul>
      {error && <p className="mt-2 text-sm text-reject">{error}</p>}
    </div>
  );
}
