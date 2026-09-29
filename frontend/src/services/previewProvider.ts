import type { AgentResponse } from "../types/agent";
export interface PreviewModel {
  title: string;
  summary: string;
  features: string[];
  fileCount: number;
  mode: string;
}
export interface PreviewProvider {
  describe(result: AgentResponse): PreviewModel;
}
// Metadata only. A future sandbox adapter must preserve isolation; never inject source into the host DOM.
export const safePreviewProvider: PreviewProvider = {
  describe(result) {
    return {
      title: result.projectName,
      summary: result.summary || result.originalPrompt,
      features: result.specification?.features ?? [],
      fileCount: result.files.length,
      mode: result.generationMode,
    };
  },
};
