export type StepStatus =
  "IDLE" | "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
export type GenerationMode = "DEMO" | "LLM";
export interface AgentError {
  code: string;
  message: string;
  retryable: boolean;
}
export interface AgentStep {
  name: string;
  status: StepStatus;
  message: string;
  durationMs?: number;
  error?: AgentError | null;
}
export interface GeneratedFile {
  path: string;
  content: string;
}
export interface ProjectSpecification {
  projectName: string;
  description: string;
  applicationType: string;
  features: string[];
  frontend: string;
  backendRequired: boolean;
}
export interface ProjectPlan {
  tasks: { id: string; title: string; type: string }[];
  fileManifest: string[];
}
export interface ValidationResult {
  valid: boolean;
  errors: string[];
  checks: string[];
}
export interface AgentExecution {
  id: string;
  events: {
    timestamp: string;
    stage: string;
    status: StepStatus;
    message: string;
  }[];
}
export interface AgentResponse {
  projectName: string;
  originalPrompt: string;
  status: StepStatus;
  steps: AgentStep[];
  files: GeneratedFile[];
  generationMode: GenerationMode;
  summary: string;
  specification: ProjectSpecification | null;
  plan: ProjectPlan | null;
  validation: ValidationResult | null;
  execution: AgentExecution;
  error: AgentError | null;
}
export interface TerminalEvent {
  time: string;
  message: string;
  error?: boolean;
}
