import { useId } from "react";
import { uploadFile } from "../lib/api.js";
import { fileSize } from "../lib/format.js";

let nextKey = 1;
export function emptyDocumentRow(type = "") {
  return { key: nextKey++, type, description: "", file: null, uploading: false, error: null };
}

/** Client-side checks for a document row; returns a message per field. */
export function validateDocumentRow(row) {
  const errors = {};
  if (!row.type) errors.type = "Choose what kind of document this is.";
  if (row.uploading) errors.file = "Wait for the upload to finish.";
  else if (!row.file) errors.file = row.error || "Upload a file for this document.";
  if (row.description.length > 200) errors.description = "Keep the note under 200 characters.";
  return errors;
}

/**
 * Array builder for the supporting documents. Each row becomes one element of the
 * `documents` process variable, which the BPMN validates with a parallel multi-instance.
 */
export default function DocumentBuilder({ rows, onChange, options, errors = [], disabled }) {
  const max = options?.maxDocuments ?? 5;
  const allowed = options?.allowedFileTypes ?? ["pdf", "jpg", "jpeg", "png"];
  const maxSize = options?.maxFileSizeBytes ?? 5 * 1024 * 1024;

  function update(key, patch) {
    onChange((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)));
  }

  function remove(key) {
    onChange((current) => current.filter((row) => row.key !== key));
  }

  async function choose(row, file) {
    if (!file) return;
    const ext = file.name.includes(".") ? file.name.split(".").pop().toLowerCase() : "";
    if (!allowed.includes(ext)) {
      update(row.key, { file: null, error: `Only ${allowed.join(", ").toUpperCase()} files can be attached.` });
      return;
    }
    if (file.size > maxSize) {
      update(row.key, { file: null, error: `This file is ${fileSize(file.size)}. The limit is ${fileSize(maxSize)}.` });
      return;
    }
    if (file.size === 0) {
      update(row.key, { file: null, error: "This file is empty." });
      return;
    }
    update(row.key, { uploading: true, error: null });
    try {
      const uploaded = await uploadFile(file);
      update(row.key, { uploading: false, file: uploaded, error: null });
    } catch (e) {
      update(row.key, { uploading: false, file: null, error: e.message });
    }
  }

  return (
    <div>
      <ol className="space-y-3">
        {rows.map((row, index) => (
          <DocumentRow
            key={row.key}
            row={row}
            index={index}
            options={options}
            allowed={allowed}
            errors={errors[index] || {}}
            disabled={disabled}
            canRemove={rows.length > 1}
            onUpdate={(patch) => update(row.key, patch)}
            onChoose={(file) => choose(row, file)}
            onRemove={() => remove(row.key)}
          />
        ))}
      </ol>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button
          type="button"
          className="btn-quiet text-sm"
          disabled={disabled || rows.length >= max}
          onClick={() => onChange((current) => [...current, emptyDocumentRow()])}
        >
          Add another document
        </button>
        <p className="text-sm text-ink-soft">
          {rows.length} of {max} documents. PDF, JPG or PNG, up to {fileSize(maxSize)} each.
        </p>
      </div>
    </div>
  );
}

function DocumentRow({ row, index, options, allowed, errors, disabled, canRemove, onUpdate, onChoose, onRemove }) {
  const id = useId();
  const accept = allowed.map((ext) => `.${ext}`).join(",");
  return (
    <li className="rounded-md border border-rule bg-paper/60 p-4">
      <div className="mb-3 flex items-center justify-between">
        <p className="font-semibold">Document {index + 1}</p>
        {canRemove && (
          <button type="button" className="text-sm font-medium text-reject hover:underline" onClick={onRemove} disabled={disabled}>
            Remove
          </button>
        )}
      </div>
      <div className="grid gap-4 md:grid-cols-[14rem_1fr]">
        <div>
          <label htmlFor={`${id}-type`} className="mb-1 block text-sm font-medium">
            Document type
          </label>
          <select
            id={`${id}-type`}
            className={`field ${errors.type ? "field-error" : ""}`}
            value={row.type}
            disabled={disabled}
            aria-invalid={Boolean(errors.type)}
            onChange={(e) => onUpdate({ type: e.target.value })}
          >
            <option value="">Choose…</option>
            {options?.documentTypes?.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
          {errors.type && <p className="mt-1 text-sm text-reject">{errors.type}</p>}
        </div>
        <div>
          <label htmlFor={`${id}-file`} className="mb-1 block text-sm font-medium">
            File
          </label>
          {row.file ? (
            <div className="flex flex-wrap items-center gap-3 rounded border border-approve/40 bg-sheet px-3 py-2">
              <p className="min-w-0 flex-1 truncate text-sm">
                <span className="font-medium text-approve">Uploaded</span> {row.file.fileName} ({fileSize(row.file.sizeBytes)})
              </p>
              <button
                type="button"
                className="text-sm font-medium text-register hover:underline"
                disabled={disabled}
                onClick={() => onUpdate({ file: null })}
              >
                Replace
              </button>
            </div>
          ) : (
            <input
              id={`${id}-file`}
              type="file"
              accept={accept}
              disabled={disabled || row.uploading}
              aria-invalid={Boolean(errors.file || row.error)}
              className={`field file:mr-3 file:rounded file:border-0 file:bg-register/10 file:px-3 file:py-1 file:font-medium file:text-register ${
                errors.file || row.error ? "field-error" : ""
              }`}
              onChange={(e) => onChoose(e.target.files?.[0])}
            />
          )}
          {row.uploading && <p className="mt-1 text-sm text-ink-soft">Uploading…</p>}
          {!row.uploading && (row.error || errors.file) && (
            <p className="mt-1 text-sm text-reject">{row.error || errors.file}</p>
          )}
        </div>
        <div className="md:col-span-2">
          <label htmlFor={`${id}-note`} className="mb-1 block text-sm font-medium">
            Note for the reviewer <span className="font-normal text-ink-soft">(optional)</span>
          </label>
          <input
            id={`${id}-note`}
            className={`field ${errors.description ? "field-error" : ""}`}
            value={row.description}
            maxLength={200}
            disabled={disabled}
            placeholder="For example: discharge summary, 3 to 14 August"
            onChange={(e) => onUpdate({ description: e.target.value })}
          />
          {errors.description && <p className="mt-1 text-sm text-reject">{errors.description}</p>}
        </div>
      </div>
    </li>
  );
}
