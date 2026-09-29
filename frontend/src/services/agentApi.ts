import type { AgentResponse, GenerationMode } from "../types/agent";
export const API_URL =
  import.meta.env?.VITE_AGENT_API_URL || "http://localhost:8080/api/agent/run";
const statuses = ["IDLE", "PENDING", "RUNNING", "COMPLETED", "FAILED"];
const record = (v: unknown): v is Record<string, unknown> =>
  !!v && typeof v === "object" && !Array.isArray(v);
const strings = (v: unknown): v is string[] =>
  Array.isArray(v) && v.every((s) => typeof s === "string");
const nullable = (v: unknown, valid: (v: Record<string, unknown>) => boolean) =>
  v === null || (record(v) && valid(v));
export function parseAgentResponse(data: unknown): AgentResponse {
  if (
    !record(data) ||
    typeof data.projectName !== "string" ||
    typeof data.originalPrompt !== "string" ||
    !statuses.includes(String(data.status)) ||
    !["DEMO", "LLM"].includes(String(data.generationMode)) ||
    typeof data.summary !== "string" ||
    !Array.isArray(data.files) ||
    data.files.length > 32 ||
    !data.files.every(
      (f) =>
        record(f) &&
        typeof f.path === "string" &&
        typeof f.content === "string" &&
        f.content.length <= 150000,
    ) ||
    !Array.isArray(data.steps) ||
    !data.steps.every(
      (s) =>
        record(s) &&
        typeof s.name === "string" &&
        statuses.includes(String(s.status)) &&
        typeof s.message === "string" &&
        (s.durationMs === undefined || typeof s.durationMs === "number"),
    ) ||
    !nullable(
      data.specification,
      (s) =>
        typeof s.projectName === "string" &&
        typeof s.description === "string" &&
        typeof s.applicationType === "string" &&
        typeof s.frontend === "string" &&
        typeof s.backendRequired === "boolean" &&
        strings(s.features),
    ) ||
    !nullable(
      data.plan,
      (p) =>
        strings(p.fileManifest) &&
        Array.isArray(p.tasks) &&
        p.tasks.every(
          (t) =>
            record(t) &&
            typeof t.id === "string" &&
            typeof t.title === "string" &&
            typeof t.type === "string",
        ),
    ) ||
    !nullable(
      data.validation,
      (v) =>
        typeof v.valid === "boolean" && strings(v.errors) && strings(v.checks),
    ) ||
    !nullable(
      data.error,
      (e) =>
        typeof e.code === "string" &&
        typeof e.message === "string" &&
        typeof e.retryable === "boolean",
    ) ||
    !record(data.execution) ||
    typeof data.execution.id !== "string" ||
    !Array.isArray(data.execution.events) ||
    !data.execution.events.every(
      (e) =>
        record(e) &&
        typeof e.timestamp === "string" &&
        Number.isFinite(Date.parse(e.timestamp)) &&
        typeof e.stage === "string" &&
        typeof e.message === "string" &&
        statuses.includes(String(e.status)),
    )
  ) {
    throw new Error(
      "Sunucudan beklenmeyen bir yanıt geldi. Lütfen tekrar dene.",
    );
  }
  if (data.status === "FAILED" && data.error === null)
    throw new Error("Sunucudan eksik hata bilgisi geldi.");
  return data as unknown as AgentResponse;
}
export async function getGenerationMode(
  signal?: AbortSignal,
): Promise<GenerationMode> {
  const response = await fetch(API_URL.replace(/\/run\/?$/, "/capabilities"), {
    signal,
  });
  if (!response.ok) throw new Error("Backend bağlantısı doğrulanamadı.");
  const data: unknown = await response.json();
  if (!record(data) || !["DEMO", "LLM"].includes(String(data.generationMode)))
    throw new Error("Üretim modu doğrulanamadı.");
  return data.generationMode as GenerationMode;
}
export async function runAgent(prompt: string): Promise<AgentResponse> {
  const controller = new AbortController();
  // Three provider stages, at most 60 seconds each. No synthetic progress timers.
  const timeout = setTimeout(() => controller.abort(), 190000);
  try {
    const response = await fetch(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt }),
      signal: controller.signal,
    });
    let data: unknown;
    try {
      data = await response.json();
    } catch {
      throw new Error(
        "Sunucudan okunamayan bir yanıt geldi. Lütfen tekrar dene.",
      );
    }
    if (record(data) && data.status === "FAILED")
      return parseAgentResponse(data);
    if (!response.ok)
      throw new Error(
        response.status === 400
          ? "Proje fikrini kontrol et. 1–10000 karakter kullanabilirsin."
          : response.status === 413
            ? "İstek çok büyük. Daha kısa bir proje açıklaması kullan."
            : `Sunucu isteği tamamlayamadı (${response.status}). Lütfen tekrar dene.`,
      );
    return parseAgentResponse(data);
  } catch (error) {
    if (error instanceof TypeError)
      throw new Error(
        "Can’a ulaşamadık. Backend bağlantısını kontrol edip tekrar dene.",
      );
    if (error instanceof DOMException && error.name === "AbortError")
      throw new Error("İstek zaman aşımına uğradı. Lütfen tekrar dene.");
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
