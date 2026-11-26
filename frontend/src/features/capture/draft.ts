import { z } from 'zod';
import type { CaptureKind } from '../../stores/ui';

// The one draft capture keeps in the browser (D-23, G-01): a reload or a crash doesn't lose what was being written.
// It is cleared on save and on discard. Photos aren't kept: a File can't go into local storage, and a 20-photo draft
// would pass its size limit. Local storage is per-browser and outlives the session, so this holds only what the user
// typed and picked, never anything the server sent beyond ids and a version number.

const STORAGE_KEY = 'memoirist.capture-draft';

export interface Draft {
  kind: CaptureKind;
  /** The id the entry is created with (D-12), so a save retried after a reload can't make a second entry. */
  clientId: string;
  /** Set once the entry exists (a save whose photos partly failed), so the next save is a PUT. */
  entryId: string | null;
  entryVersion: number | null;
  entryDate: string;
  templateId: string | null;
  /** The chosen template's prompts, so the ones left empty can be removed on save (D-06). */
  prompts: string[];
  title: string;
  /** The editor's TipTap document (D-05), or null before anything was typed. */
  content: Record<string, unknown> | null;
  tagIds: string[];
  /** The Spotify URI picked (D-35, task 4.15), or null. Absent from a draft written before 4.15, which then takes the
   *  entry's own soundtrack when it is read back. */
  soundtrack?: string | null;
  /** When it was last written, for "Draft saved 7:41 pm". */
  savedAt: string;
}

const id = z.string().min(1).max(64);

// TODO(human) PRACTICE: Write DraftSchema, the zod schema that readDraft uses to check what came out of local
// storage before capture trusts it. Every field of Draft must be checked; `content` is a TipTap document whose
// `type` is "doc" (other keys allowed), or null.
// Hint: local storage is untrusted input, like a response body: another version of the app, an extension or the user
// can leave anything there. z.object() drops unknown keys and safeParse() never throws, so a bad draft becomes "no
// draft" instead of a crash. z.looseObject keeps the document's own keys; z.iso.date() checks "2026-11-24".
// `satisfies z.ZodType<Draft>` makes the compiler say when the schema and the interface drift apart.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
const DraftSchema = z.object({
  kind: z.enum(['entry', 'dump', 'page']),
  clientId: id,
  entryId: id.nullable(),
  entryVersion: z.number().int().nonnegative().nullable(),
  entryDate: z.iso.date(),
  templateId: id.nullable(),
  // No count limit: each template chosen adds up to 20 prompts (API_CONTRACT §Templates), and a capped list would
  // drop the whole draft, typed text included. Local storage's own size limit bounds it (review fix, task 4.6).
  prompts: z.array(z.string().max(200)),
  title: z.string().max(150),
  content: z.looseObject({ type: z.literal('doc') }).nullable(),
  tagIds: z.array(id).max(20),
  // Task 4.15: exactly what the server stores (D-35), so a tampered draft can't send anything else.
  soundtrack: z
    .string()
    .regex(/^spotify:(track|album|playlist):[A-Za-z0-9]{22}$/)
    .nullable()
    .optional(),
  savedAt: z.iso.datetime(),
}) satisfies z.ZodType<Draft>;
// └── END ANSWER ▲ ──

/** The saved draft, or null when there is none or it isn't one this version of the app understands. */
export function readDraft(): Draft | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) return null;
    const parsed = DraftSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : null;
  } catch {
    // Storage blocked (a private window) or not JSON: capture works without a draft.
    return null;
  }
}

export function writeDraft(draft: Draft): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
  } catch {
    // Full or blocked storage: the draft is a safety net, so writing goes on without it.
  }
}

export function clearDraft(): void {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing to clear.
  }
}
