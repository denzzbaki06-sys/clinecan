import test from "node:test";
import assert from "node:assert/strict";
import { parseAgentResponse, runAgent } from "../src/services/agentApi.ts";
import { safePreviewProvider } from "../src/services/previewProvider.ts";
import { executionEvents, modeLabel } from "../src/services/execution.ts";
const result = (mode = "DEMO") => ({
  projectName: "test",
  originalPrompt: "test prompt",
  status: "COMPLETED",
  steps: [
    {
      name: "ANALYZE",
      status: "COMPLETED",
      message: "understood",
      durationMs: 2,
    },
  ],
  files: [{ path: "src/App.tsx", content: "<script>not executed</script>" }],
  generationMode: mode,
  summary: "A test project",
  specification: null,
  plan: null,
  validation: { valid: true, errors: [], checks: ["static"] },
  execution: {
    id: "test",
    events: [
      {
        timestamp: "2026-09-28T12:00:00Z",
        stage: "ANALYZE",
        status: "COMPLETED",
        message: "actual event",
      },
    ],
  },
  error: null,
});
test("accepts DEMO and LLM response contracts", () => {
  for (const mode of ["DEMO", "LLM"])
    assert.equal(parseAgentResponse(result(mode)).generationMode, mode);
});
test("rejects malformed response and unknown mode", () => {
  assert.throws(() => parseAgentResponse({}));
  assert.throws(() => parseAgentResponse(result("FAKE")));
});
test("mode label never claims connection before success", () => {
  assert.equal(modeLabel("DEMO", null), "Demo Mode");
  assert.equal(modeLabel("LLM", null), "AI Configured");
  assert.equal(modeLabel("LLM", result("LLM")), "AI Connected");
  assert.equal(
    modeLabel("LLM", { ...result("LLM"), status: "FAILED" }),
    "AI Connection Error",
  );
});
test("preview consumes metadata only", () => {
  const preview = safePreviewProvider.describe(result());
  assert.equal(preview.summary, "A test project");
  assert.equal(preview.fileCount, 1);
  assert.equal(JSON.stringify(preview).includes("<script>"), false);
});
test("execution log uses server events", () => {
  assert.match(executionEvents(result())[0].message, /actual event/);
});
test("real failure payload retains failed stages and mode", async () => {
  const saved = globalThis.fetch;
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        ...result("LLM"),
        status: "FAILED",
        files: [],
        error: {
          code: "PROVIDER_AUTH",
          message: "Invalid provider key",
          retryable: false,
        },
      }),
      { status: 502 },
    );
  try {
    const data = await runAgent("test");
    assert.equal(data.status, "FAILED");
    assert.equal(data.error.code, "PROVIDER_AUTH");
  } finally {
    globalThis.fetch = saved;
  }
});
test("network failure has friendly message", async () => {
  const saved = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("network");
  };
  try {
    await assert.rejects(runAgent("test"), /ulaşamadık/);
  } finally {
    globalThis.fetch = saved;
  }
});
test("server failure has friendly message", async () => {
  const saved = globalThis.fetch;
  globalThis.fetch = async () => new Response("{}", { status: 500 });
  try {
    await assert.rejects(runAgent("test"), /500/);
  } finally {
    globalThis.fetch = saved;
  }
});
test("malformed server JSON is rejected", async () => {
  const saved = globalThis.fetch;
  globalThis.fetch = async () => new Response("bad");
  try {
    await assert.rejects(runAgent("test"), /okunamayan/);
  } finally {
    globalThis.fetch = saved;
  }
});
test("timeout is distinguished from success", async () => {
  const saved = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new DOMException("Aborted", "AbortError");
  };
  try {
    await assert.rejects(runAgent("test"), /zaman aşımına/);
  } finally {
    globalThis.fetch = saved;
  }
});
