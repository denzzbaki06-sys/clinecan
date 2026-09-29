import { API_URL, parseAgentResponse } from "./agentApi.ts";
import type { AgentResponse, AgentStep, GenerationMode, TerminalEvent } from "../types/agent.ts";
export interface Progress { sequence: number; executionId: string; timestamp: string; stage: string; status: "RUNNING" | "COMPLETED" | "FAILED"; message: string }
export interface ExecutionSnapshot {
  id: string; status: "CREATED" | "RUNNING" | "REPAIRING" | "COMPLETED" | "FAILED" | "CANCELLED" | "TIMED_OUT";
  generationMode: GenerationMode; events: Progress[]; result: AgentResponse | null;
  build: { success: boolean; exitCode: number; stdout: string; stderr: string; durationMs: number; failureCategory: string | null; logsTruncated: boolean } | null;
  preview: { available: boolean; kind: string; downloadUrl: string | null; bytes: number; entryUrl: string | null };
  error: { code: string; message: string; retryable: boolean } | null; repairAttempts: number;
}
const BASE = API_URL.replace(/\/run\/?$/, "/executions");
const terminal = new Set(["COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT"]);
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === "object" && !Array.isArray(v);
export function parseProgress(value: unknown, id: string): Progress {
  if (!object(value) || value.executionId !== id || !Number.isSafeInteger(value.sequence) || Number(value.sequence) < 1 || Number(value.sequence) > 100 ||
      typeof value.timestamp !== "string" || !Number.isFinite(Date.parse(value.timestamp)) || typeof value.stage !== "string" ||
      !["RUNNING", "COMPLETED", "FAILED"].includes(String(value.status)) || typeof value.message !== "string" || value.message.length > 8000)
    throw new Error("Geçersiz yürütme olayı.");
  return value as unknown as Progress;
}
export function parseExecution(value: unknown): ExecutionSnapshot {
  if (!object(value) || typeof value.id !== "string" || !/^[a-f0-9-]{36}$/.test(value.id) ||
      !["CREATED", "RUNNING", "REPAIRING", ...terminal].includes(String(value.status)) || !["DEMO", "LLM"].includes(String(value.generationMode)) ||
      !Array.isArray(value.events) || value.events.length > 100 || !object(value.preview) || typeof value.preview.available !== "boolean" ||
      !Number.isInteger(value.repairAttempts) || Number(value.repairAttempts) < 0 || Number(value.repairAttempts) > 2)
    throw new Error("Geçersiz yürütme yanıtı.");
  value.events.forEach(e => parseProgress(e, String(value.id)));
  if (value.result !== null) parseAgentResponse(value.result);
  if (value.error !== null && (!object(value.error) || typeof value.error.message !== "string" || typeof value.error.code !== "string")) throw new Error("Geçersiz hata yanıtı.");
  if (value.build !== null && (!object(value.build) || typeof value.build.success !== "boolean" || typeof value.build.durationMs !== "number")) throw new Error("Geçersiz derleme yanıtı.");
  if (value.preview.available) {
    if (value.status !== "COMPLETED" || !object(value.build) || value.build.success !== true ||
        value.preview.downloadUrl !== `/api/agent/executions/${value.id}/artifact` ||
        typeof value.preview.entryUrl !== "string" ||
        !new RegExp(`^/api/preview/${value.id}/[a-f0-9]{64}/index\\.html$`).test(value.preview.entryUrl))
      throw new Error("Geçersiz preview/artifact yanıtı.");
  } else if (value.preview.entryUrl !== null && value.preview.entryUrl !== undefined) {
    throw new Error("Geçersiz preview yanıtı.");
  }
  return value as unknown as ExecutionSnapshot;
}
export function liveSteps(events: Progress[]): AgentStep[] {
  const steps = new Map<string, AgentStep>(["ANALYZE", "PLAN", "GENERATE", "VALIDATE", "BUILD"].map(name => [name, { name, status: "PENDING", message: "Bekliyor" }]));
  for (const event of events) if (!["INITIALIZE", "FINISH"].includes(event.stage)) steps.set(event.stage, { name: event.stage, status: event.status, message: event.message });
  return [...steps.values()];
}
export function terminalEvent(e: Progress): TerminalEvent {
  return { time: new Date(e.timestamp).toLocaleTimeString("tr-TR"), message: `${e.stage} · ${e.status}: ${e.message}`, error: e.status === "FAILED" };
}
export function artifactUrl(snapshot: ExecutionSnapshot | null): string | null {
  return snapshot?.status === "COMPLETED" && snapshot.preview.available && snapshot.build?.success ? `${BASE}/${snapshot.id}/artifact` : null;
}
export interface EventStream { addEventListener(type: string, listener: (event: MessageEvent) => void): void; onerror: ((event: Event) => void) | null; close(): void }
export async function runExecution(prompt: string, onProgress: (e: Progress) => void,
  options: { signal?: AbortSignal; timeoutMs?: number; open?: (url: string) => EventStream } = {}): Promise<ExecutionSnapshot> {
  const response = await fetch(BASE, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ prompt }), signal: options.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(response.status === 429 ? "Can şu an meşgul. Biraz sonra tekrar dene." : "Yürütme başlatılamadı.");
  const initial = parseExecution(await response.json());
  return new Promise((resolve, reject) => {
    let stream: EventStream | undefined; let ended = false; let last = 0; let polling = false;
    const stop = () => { ended = true; stream?.close(); clearTimeout(timeout); options.signal?.removeEventListener("abort", abort); };
    const fail = (message: string) => { if (!ended) { stop(); reject(new Error(message)); } };
    const cancel = () => { void fetch(`${BASE}/${initial.id}`, { method: "DELETE", signal: AbortSignal.timeout(5000) }).catch(() => {}); };
    const abort = () => { cancel(); fail("İşlem iptal edildi."); };
    const timeout = setTimeout(() => { cancel(); fail("İşlem zaman aşımına uğradı."); }, options.timeoutMs ?? 910000);
    const deliver = (e: Progress) => { if (e.sequence > last) { last = e.sequence; onProgress(e); } };
    const finish = (snapshot: ExecutionSnapshot) => {
      if (ended) return;
      if (snapshot.id !== initial.id) { fail("Yürütme kimliği eşleşmiyor."); return; }
      snapshot.events.forEach(deliver);
      if (terminal.has(snapshot.status)) { stop(); resolve(snapshot); }
    };
    options.signal?.addEventListener("abort", abort, { once: true });
    if (options.signal?.aborted) { abort(); return; }
    finish(initial); if (ended) return;
    try { stream = (options.open ?? (url => new EventSource(url)))(`${BASE}/${initial.id}/events`); }
    catch { cancel(); fail("Canlı bağlantı açılamadı."); return; }
    stream.addEventListener("progress", event => { if (ended) return; try { deliver(parseProgress(JSON.parse(event.data), initial.id)); } catch { fail("Canlı olay okunamadı."); } });
    stream.addEventListener("complete", event => { try { finish(parseExecution(JSON.parse(event.data))); } catch { fail("Yürütme sonucu okunamadı."); } });
    // Native EventSource reconnects with Last-Event-ID. Fetch the terminal state if the last frame was lost.
    stream.onerror = () => {
      if (ended || polling) return; polling = true;
      void fetch(`${BASE}/${initial.id}`, { signal: AbortSignal.timeout(10000) }).then(async r => {
        if (r.status === 404) { fail("Yürütmenin süresi doldu."); return; }
        if (r.ok) finish(parseExecution(await r.json()));
      }).catch(() => {}).finally(() => { polling = false; });
    };
  });
}

export function executionResult(snapshot: ExecutionSnapshot): AgentResponse | null {
  if (!snapshot.result) return null;
  return { ...snapshot.result, status: snapshot.status === "COMPLETED" ? "COMPLETED" : "FAILED",
    error: snapshot.error, steps: liveSteps(snapshot.events),
    execution: { id: snapshot.id, events: snapshot.events.map(e => ({ timestamp: e.timestamp, stage: e.stage, status: e.status, message: e.message })) } };
}

export function previewUrl(snapshot: ExecutionSnapshot | null): string | null {
  if (!snapshot || snapshot.status !== "COMPLETED" || !snapshot.preview.available || !snapshot.build?.success || !snapshot.preview.entryUrl) return null;
  return `${new URL(API_URL).origin}${snapshot.preview.entryUrl}`;
}
