import type {
  AgentResponse,
  GenerationMode,
  TerminalEvent,
} from "../types/agent";
export function executionEvents(result: AgentResponse): TerminalEvent[] {
  return result.execution.events.map((e) => ({
    time: new Date(e.timestamp).toLocaleTimeString("tr-TR"),
    message: `${e.stage} · ${e.status}: ${e.message}`,
    error: e.status === "FAILED",
  }));
}
export function modeLabel(
  mode: GenerationMode | null,
  result: AgentResponse | null,
): string {
  if (result?.generationMode === "LLM" && result.error && ["COMPILATION", "BUILD_TIMEOUT", "BUILD_FAILED", "SANDBOX_UNAVAILABLE", "DEPENDENCY_POLICY", "ARTIFACT_INVALID", "UNSAFE_REPAIR"].includes(result.error.code))
    return "AI Connected";
  if (result?.generationMode === "LLM" && result.status === "FAILED")
    return "AI Connection Error";
  if (result?.generationMode === "LLM" && result.status === "COMPLETED")
    return "AI Connected";
  if ((result?.generationMode ?? mode) === "DEMO") return "Demo Mode";
  if (mode === "LLM") return "AI Configured";
  return "Mode Unverified";
}
