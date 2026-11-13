// Display helpers: plain words for statuses, Indian number formatting.

export const STATUS = {
  SUBMITTED: { label: "Being checked", tone: "pending" },
  UNDER_REVIEW: { label: "Under review", tone: "pending" },
  AWAITING_PAYMENT: { label: "Fee due", tone: "action" },
  PROCESSING: { label: "Finalising", tone: "pending" },
  APPROVED: { label: "Condoned", tone: "approve" },
  REJECTED: { label: "Not condoned", tone: "reject" },
};

export const FINAL_STATUSES = ["APPROVED", "REJECTED"];

export const ROLE_TITLES = {
  STUDENT: "Student",
  FA: "Faculty Advisor",
  AA: "Academic Advisor",
  HOD: "Head of Department",
  ADMIN: "Admin",
};

export const SLABS = {
  SLAB_A: "Slab A (70-75%)",
  SLAB_B: "Slab B (65-70%)",
  SLAB_C: "Slab C (60-65%, medical)",
  NOT_ELIGIBLE: "Not eligible",
  NOT_REQUIRED: "Not required",
};

export const REJECTION_STAGES = {
  ATTENDANCE: "Attendance check",
  ELIGIBILITY: "Eligibility check",
  DOCUMENTS: "Document verification",
  FA_APPROVAL: "Faculty Advisor review",
  AA_APPROVAL: "Academic Advisor review",
  HOD_APPROVAL: "HOD review",
  FEE: "Fee payment",
};

const dateTime = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});
const dateOnly = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });
const time = new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit", second: "2-digit" });

export function formatDateTime(value) {
  return value ? dateTime.format(new Date(value)) : "";
}

export function formatDate(value) {
  return value ? dateOnly.format(new Date(value)) : "";
}

export function formatTime(value) {
  return value ? time.format(new Date(value)) : "";
}

export function rupees(amount) {
  if (amount === null || amount === undefined || amount === "") return "–";
  return `₹${Number(amount).toLocaleString("en-IN")}`;
}

export function percent(value) {
  if (value === null || value === undefined) return "–";
  return `${Number(value).toFixed(2)}%`;
}

export function fileSize(bytes) {
  if (!bytes && bytes !== 0) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function slabLabel(slab) {
  return slab ? SLABS[slab] || slab.replaceAll("_", " ") : "Not decided yet";
}
