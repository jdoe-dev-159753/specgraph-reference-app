import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath, pathToFileURL } from "node:url";

const sourceDir = path.dirname(fileURLToPath(import.meta.url));
const workspace = path.resolve(sourceDir, "..", "..");
const buildDir = path.join(sourceDir, ".build", "v08");
const packageDir = path.join(buildDir, "pptx-root");
const previewDir = path.join(buildDir, "preview");
const iconDir = path.join(sourceDir, ".build", "v05", "icons");
const outputDir = path.join(sourceDir, "output");
const zipPath = path.join(buildDir, "SpecGraph_presentation_working_v0.8.zip");
const draftPath = path.join(buildDir, "SpecGraph_presentation_working_v0.8.draft.pptx");
const candidatePath = path.join(buildDir, "SpecGraph_presentation_working_v0.8.candidate.pptx");
const validationReceiptPath = path.join(buildDir, "SpecGraph_presentation_working_v0.8.validation.json");
const pptxPath = path.join(outputDir, "SpecGraph_presentation_working_v0.8.pptx");
const screenshotDir = path.join(sourceDir, "assets", "screenshots", "crops");
const runtimeModules = process.env.RUNTIME_NODE_MODULES || sourceDir;
const runtimeRequire = createRequire(path.join(runtimeModules, "package.json"));
const sharp = runtimeRequire("sharp");
const JSZip = runtimeRequire("jszip");
let lucide = null;
try {
  lucide = runtimeRequire("lucide");
} catch {
  // Reuse the retained v0.5 icon cache when the current runtime omits Lucide.
}

const W = 1280;
const H = 720;
const EMU = 9525;
const SLIDE_CX = W * EMU;
const SLIDE_CY = H * EMU;
const C = {
  ink: "17212B",
  navy: "101820",
  muted: "5D6873",
  pale: "F4F6F8",
  line: "D9DEE3",
  white: "FFFFFF",
  red: "D71920",
  redDark: "981B1E",
  redSoft: "FCEBEC",
  green: "1F7A5A",
  greenSoft: "EAF5F0",
  amber: "B46B13",
  amberSoft: "FFF4E3",
  blue: "2A5B84",
  blueSoft: "EAF1F7",
  slate: "384754",
  light: "FAFBFC",
};

const esc = (s) => String(s ?? "")
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&apos;");
const e = (n) => Math.round(n * EMU);
const rgb = (hex) => hex.replace("#", "").toUpperCase();

let shapeId = 1;
const text = (x, y, w, h, value, options = {}) => ({ type: "text", x, y, w, h, value, ...options });
const rect = (x, y, w, h, options = {}) => ({ type: "rect", x, y, w, h, ...options });
const ellipse = (x, y, w, h, options = {}) => ({ type: "ellipse", x, y, w, h, ...options });
const line = (x, y, w, h, options = {}) => ({ type: "line", x, y, w, h, ...options });
const image = (x, y, w, h, src) => ({ type: "image", x, y, w, h, src, fit: "contain" });
const icon = (x, y, size, name, color = C.ink) => ({
  type: "image",
  x,
  y,
  w: size,
  h: size,
  src: path.join(iconDir, `${name}-${rgb(color)}.png`),
  iconName: name,
  iconColor: color,
});

function iconBadge(x, y, size, name, color, fill = C.white, square = false) {
  const frame = square
    ? rect(x, y, size, size, { fill, line: color, width: 1.5, radius: 10 })
    : ellipse(x, y, size, size, { fill, line: color, width: 1.5 });
  const inset = Math.round(size * 0.25);
  return [frame, icon(x + inset, y + inset, size - inset * 2, name, color)];
}

function footer(slideNo, appendix = false) {
  return [
    line(58, 674, 1164, 0, { color: appendix ? "BFC5CB" : C.line, width: 1 }),
    text(60, 682, 520, 18, appendix ? "TECHNICAL APPENDIX" : "CUSTOMER ACTIVITY ANALYTICS", { size: 10, color: C.muted, bold: true, tracking: 120 }),
    text(1160, 682, 60, 18, String(slideNo), { size: 10, color: C.muted, align: "right" }),
  ];
}

function titleBlock(titleValue, kicker) {
  const shapes = [];
  if (kicker) shapes.push(text(60, 32, 1160, 22, kicker.toUpperCase(), { size: 11, color: C.red, bold: true, tracking: 180 }));
  shapes.push(text(58, kicker ? 58 : 42, 1164, 82, titleValue, { size: 30, color: C.ink, bold: true, valign: "mid" }));
  return shapes;
}

function note(anchor, script, caution, transition, time, question, answer, sources) {
  return [
    `ANCHOR\n${anchor}`,
    `SCRIPT\n${script}`,
    `DO NOT SAY\n${caution}`,
    `TRANSITION\n${transition}`,
    `TIME\n${time}`,
    `LIKELY QUESTION\n${question}`,
    `DIRECT ANSWER\n${answer}`,
    `SOURCES\n${sources.join("\n")}`,
  ].join("\n\n");
}

function factorial(n) {
  let value = 1;
  for (let i = 2; i <= n; i++) value *= i;
  return value;
}

function betaPdf(value, alpha, beta) {
  const coefficient = factorial(alpha + beta - 1) / (factorial(alpha - 1) * factorial(beta - 1));
  return coefficient * Math.pow(value, alpha - 1) * Math.pow(1 - value, beta - 1);
}

function betaDistributionPlot(x, y, width, height) {
  const left = x + 64;
  const right = x + width - 24;
  const top = y + 34;
  const bottom = y + height - 72;
  const plotWidth = right - left;
  const plotHeight = bottom - top;
  const densityMax = 4.2;
  const px = (value) => left + value * plotWidth;
  const py = (density) => bottom - Math.min(densityMax, density) / densityMax * plotHeight;
  const shapes = [];

  for (let d = 1; d <= 4; d++) {
    const gridY = py(d);
    shapes.push(line(left, gridY, plotWidth, 0, { color: "E8EBEE", width: 1 }));
    shapes.push(text(left - 38, gridY - 8, 30, 18, String(d), { size: 10, color: C.muted, align: "right" }));
  }

  for (let i = 20; i <= 50; i++) {
    const value = i / 50;
    const curveY = py(betaPdf(value, 4, 5));
    shapes.push(line(px(value), curveY, 0, bottom - curveY, { color: "FCEBEC", width: 7 }));
  }

  shapes.push(line(left, top, 0, plotHeight, { color: C.slate, width: 1.5 }));
  shapes.push(line(left, bottom, plotWidth, 0, { color: C.slate, width: 1.5 }));

  for (let i = 0; i <= 5; i++) {
    const value = i / 5;
    shapes.push(line(px(value), bottom, 0, 7, { color: C.slate, width: 1 }));
    shapes.push(text(px(value) - 24, bottom + 10, 48, 18, value.toFixed(1), { size: 10, color: C.muted, align: "center" }));
  }

  for (const [alpha, beta, color, thickness] of [[1, 4, C.blue, 3], [4, 5, C.red, 4]]) {
    for (let i = 0; i < 80; i++) {
      const v1 = i / 80;
      const v2 = (i + 1) / 80;
      shapes.push(line(px(v1), py(betaPdf(v1, alpha, beta)), px(v2) - px(v1), py(betaPdf(v2, alpha, beta)) - py(betaPdf(v1, alpha, beta)), { color, width: thickness }));
    }
  }

  const referenceX = px(0.40);
  shapes.push(line(referenceX, top, 0, plotHeight, { color: C.amber, width: 2 }));
  shapes.push(text(referenceX - 76, top - 28, 152, 20, "reference p = 0.40", { size: 11, color: C.amber, bold: true, align: "center" }));
  shapes.push(text(left, top - 30, 190, 20, "probability density f(p)", { size: 11, color: C.muted, bold: true }));
  shapes.push(text(left + 160, bottom + 40, plotWidth - 320, 22, "latent review-elevated rate p", { size: 12, color: C.ink, bold: true, align: "center" }));
  shapes.push(line(left + 20, top + 42, 34, 0, { color: C.blue, width: 3 }));
  shapes.push(text(left + 62, top + 30, 250, 24, "prior Beta(1, 4), mean 0.20", { size: 12, color: C.blue, bold: true }));
  shapes.push(line(left + 20, top + 76, 34, 0, { color: C.red, width: 4 }));
  shapes.push(text(left + 62, top + 64, 300, 24, "posterior Beta(4, 5), mean 0.44", { size: 12, color: C.redDark, bold: true }));
  shapes.push(text(px(0.68), top + 70, 250, 52, "shaded posterior mass\nP(p > 0.40) = 0.594", { size: 13, color: C.redDark, bold: true, align: "center" }));
  return shapes;
}

function fuzzyPartitionChart(x, y, width, height) {
  const left = x + 72;
  const right = x + width - 24;
  const top = y + 22;
  const bottom = y + height - 38;
  const plotWidth = right - left;
  const px = (value) => left + plotWidth * value;
  const shapes = [
    line(left, top, 0, bottom - top, { color: C.slate, width: 1 }),
    line(left, bottom, plotWidth, 0, { color: C.slate, width: 1 }),
    text(x, y - 10, width, 22, "Shared low / medium / high partition over every effective ratio", { size: 14, color: C.ink, bold: true, align: "center" }),
    text(left - 52, top - 8, 40, 18, "1", { size: 10, color: C.muted, align: "right" }),
    text(left - 52, bottom - 8, 40, 18, "0", { size: 10, color: C.muted, align: "right" }),
    line(px(0.00), top, px(0.10) - px(0.00), 0, { color: C.blue, width: 4 }),
    line(px(0.10), top, px(0.30) - px(0.10), bottom - top, { color: C.blue, width: 4 }),
    line(px(0.10), bottom, px(0.30) - px(0.10), top - bottom, { color: C.amber, width: 4 }),
    line(px(0.30), top, px(0.60) - px(0.30), bottom - top, { color: C.amber, width: 4 }),
    line(px(0.30), bottom, px(0.60) - px(0.30), top - bottom, { color: C.green, width: 4 }),
    line(px(0.60), top, px(1.00) - px(0.60), 0, { color: C.green, width: 4 }),
    text(px(0.02), top + 10, 96, 20, "LOW", { size: 11, color: C.blue, bold: true }),
    text(px(0.25), top + 10, 130, 20, "MEDIUM", { size: 11, color: C.amber, bold: true, align: "center" }),
    text(px(0.68), top + 10, 96, 20, "HIGH", { size: 11, color: C.green, bold: true, align: "center" }),
  ];
  for (const value of [0.00, 0.10, 0.30, 0.60, 1.00]) {
    shapes.push(line(px(value), bottom, 0, 7, { color: C.slate, width: 1 }));
    shapes.push(text(px(value) - 30, bottom + 10, 60, 18, value.toFixed(2), { size: 9, color: C.muted, align: "center" }));
  }
  return shapes;
}

const slides = [
  {
    bg: C.light,
    shapes: [
      line(64, 72, 88, 0, { color: C.red, width: 5 }),
      text(64, 110, 760, 28, "CUSTOMER ACTIVITY ANALYTICS", { size: 15, color: C.redDark, bold: true, tracking: 220 }),
      text(62, 168, 1080, 152, "A working operator path built through a controlled human-agent system", { size: 43, color: C.ink, bold: true }),
      text(66, 350, 940, 52, "Five-day, production-shaped demonstrator for a Swissquote interview exercise", { size: 22, color: C.muted }),
      line(66, 470, 1138, 0, { color: C.line, width: 1 }),
      text(66, 504, 330, 26, "PRODUCT PROOF", { size: 12, color: C.red, bold: true, tracking: 140 }),
      text(66, 536, 450, 58, "Customer activity, evidence, analysis and history", { size: 19, color: C.ink, bold: true }),
      text(650, 504, 330, 26, "METHOD PROOF", { size: 12, color: C.blue, bold: true, tracking: 140 }),
      text(650, 536, 490, 58, "Specifications, work graph, ratchets and review", { size: 19, color: C.ink, bold: true }),
      text(66, 632, 580, 24, "Nicolas Cazin · working deck v0.8", { size: 13, color: C.muted }),
      text(982, 632, 220, 24, "submission-v1 at final freeze", { size: 13, color: C.muted, align: "right" }),
    ],
    notes: note(
      "The exercise produced a product result and a delivery-system result.",
      "I will start with the answer. The demonstrator runs a complete Customer Care review path. The same work also exercises a controlled way for one accountable engineer to coordinate several AI agents through specifications, GitHub work state, deterministic checks and review evidence.",
      "Do not start with frameworks, algorithms or issue numbers. Do not claim production readiness.",
      "The next slide states both results before the supporting detail.",
      "00:20",
      "What exactly did the exercise produce?",
      "A production-shaped application demonstrator and a reviewable engineering method for building it with AI assistance.",
      ["GitHub issue #229", "README.md", "docs/assignment/Inception/Inception.md", "https://barbaraminto.com/", "https://www.assertion-evidence.com/"]
    ),
  },
  {
    shapes: [
      ...titleBlock("The pilot produced a working operator path and a controlled delivery system", "Answer first"),
      rect(58, 168, 650, 406, { fill: C.white, line: C.line, width: 1, radius: 0 }),
      image(70, 180, 626, 390, path.join(screenshotDir, "R4_context_landscape.png")),
      line(746, 190, 0, 360, { color: C.line, width: 2 }),
      ellipse(792, 184, 58, 58, { fill: C.red, line: C.red }),
      text(792, 197, 58, 32, "1", { size: 20, color: C.white, bold: true, align: "center", valign: "mid" }),
      text(874, 182, 300, 34, "Product proof", { size: 22, color: C.ink, bold: true }),
      text(874, 224, 318, 96, "An authenticated operator inspects activity, runs grounded analysis and reopens persisted history.", { size: 16, color: C.muted }),
      ellipse(792, 350, 58, 58, { fill: C.blue, line: C.blue }),
      text(792, 363, 58, 32, "2", { size: 20, color: C.white, bold: true, align: "center", valign: "mid" }),
      text(874, 348, 300, 34, "Method proof", { size: 22, color: C.ink, bold: true }),
      text(874, 390, 318, 100, "One human coordinated agents through specifications, GitHub state, deterministic gates and review evidence.", { size: 16, color: C.muted }),
      rect(780, 510, 420, 76, { fill: C.ink, line: C.ink, radius: 8 }),
      text(792, 524, 396, 48, "Human accountability covers customer review and accepted change", { size: 15.5, color: C.white, bold: true, align: "center", valign: "mid" }),
      ...footer(2),
    ],
    notes: note(
      "Two results answer the exercise before any architecture detail.",
      "The first result is visible in the browser. The second is visible in the repository and its GitHub work graph. The two belong together because a financial-services demonstration needs reviewable evidence for both the recommendation and the way the software change was accepted.",
      "Do not imply that AI agents accept their own work. The human accepts the change and the operator retains the decision.",
      "I will first show the operator loop, then explain the trust architecture and finally the delivery control plane.",
      "00:40",
      "Why present the engineering process as a result?",
      "The role is a pilot. The repeatable delivery cell matters alongside the application because other engineers could later use the same controlled pattern.",
      ["docs/assignment/Inception/Inception.md sections 1, 24 and 28", "GitHub issue #229", "Authentic R4 Playwright artifact 9856114241"]
    ),
  },
  {
    shapes: [
      ...titleBlock("The operator moves from source facts to a recommendation and back to reviewable history", "Customer Care loop"),
      line(244, 302, 56, 0, { color: C.red, width: 3, arrow: true }),
      line(484, 302, 56, 0, { color: C.red, width: 3, arrow: true }),
      line(724, 302, 56, 0, { color: C.red, width: 3, arrow: true }),
      line(964, 302, 56, 0, { color: C.red, width: 3, arrow: true }),
      line(1112, 438, 0, 72, { color: C.blue, width: 2 }),
      line(1112, 510, -720, 0, { color: C.blue, width: 2 }),
      line(392, 510, 0, -72, { color: C.blue, width: 2, arrow: true }),
      ...[
        [60, "01", "Identify", "Authenticated operator", "UserRoundCheck", C.red],
        [300, "02", "Review", "Activity facts and source risk", "Activity", C.blue],
        [540, "03", "Analyze", "Bounded evidence pipeline", "BrainCircuit", C.redDark],
        [780, "04", "Inspect", "Grounding and provenance", "BookOpenCheck", C.green],
        [1020, "05", "Reopen", "Persisted review context", "History", C.red],
      ].flatMap(([x, n, h, b, asset, color], i) => [
        rect(x, 224, 184, 214, { fill: i === 4 ? C.redSoft : C.white, line: i === 4 ? C.red : C.line, width: i === 4 ? 2 : 1, radius: 12 }),
        rect(x, 224, 184, 6, { fill: color, line: color, radius: 0 }),
        text(x + 16, 240, 40, 24, n, { size: 12, color, bold: true }),
        ...iconBadge(x + 60, 246, 64, asset, color, i === 4 ? C.white : C.pale),
        text(x + 16, 326, 152, 30, h, { size: 19, color: C.ink, bold: true, align: "center" }),
        text(x + 16, 366, 152, 48, b, { size: 14, color: C.muted, align: "center" }),
      ]),
      text(500, 482, 500, 24, "A later review resumes from retained context", { size: 14, color: C.blue, bold: true, align: "center" }),
      rect(208, 566, 864, 66, { fill: C.pale, line: C.line, radius: 0 }),
      ...iconBadge(226, 577, 42, "ShieldCheck", C.red, C.white),
      text(288, 580, 760, 38, "The recommendation remains advisory. The operator keeps the decision.", { size: 17, color: C.ink, bold: true, align: "center", valign: "mid" }),
      ...footer(3),
    ],
    notes: note(
      "The product is a human review loop with memory.",
      "The main reading direction is left to right. The loop closes only after a completed analysis has been persisted. A later operator review reopens the same customer context, recommendation, grounding and provenance instead of starting from an isolated generated sentence.",
      "Do not describe this as an automated customer decision or as continuous transaction monitoring.",
      "The browser exposes the same chain directly.",
      "00:40",
      "Where is the human in the loop?",
      "The operator decides how to use the recommendation. The system supplies a reviewable trail and never writes generated content back as source risk truth.",
      ["docs/assignment/SRS/SRS.md", "docs/assignment/SDD/diagrams/activity-customer-review.puml", "docs/assignment/SDD/diagrams/activity-grounded-analysis.puml", "Lucide icon library, ISC license"]
    ),
  },
  {
    shapes: [
      ...titleBlock("R5 closes the live loop from WatchInfra to LM Studio and back to browser history", "Delivered interview demonstrator"),
      rect(58, 170, 570, 402, { fill: C.white, line: C.line, width: 1, radius: 0 }),
      image(68, 180, 550, 382, path.join(screenshotDir, "R5_analysis_landscape.png")),
      rect(652, 170, 570, 402, { fill: C.white, line: C.line, width: 1, radius: 0 }),
      image(662, 180, 550, 382, path.join(screenshotDir, "R5_history_landscape.png")),
      text(76, 188, 206, 30, "1  ANALYSIS RESULT", { size: 12, color: C.white, bold: true, fill: C.red, margin: 7, valign: "mid" }),
      text(670, 188, 224, 30, "2  LOCAL PROVENANCE", { size: 12, color: C.white, bold: true, fill: C.blue, margin: 7, valign: "mid" }),
      rect(58, 590, 1164, 54, { fill: C.ink, line: C.ink, radius: 8 }),
      text(76, 600, 1128, 34, "Bayesian + fuzzy + Random Forest · pgvector + MiniLM · Ministral through LM Studio · external transmission: no", { size: 14, color: C.white, bold: true, align: "center", valign: "mid" }),
      ...footer(4),
    ],
    notes: note(
      "R5 is the delivered full interview configuration, not a roadmap placeholder.",
      "WatchInfra runs the registered OCI Compose package. The authenticated operator reviews customer 444 and starts analysis. Stage 1 preserves separate Bayesian, fuzzy and Random Forest artifacts. Stage 2 retrieves synthetic policy through PostgreSQL, pgvector and local MiniLM embeddings. Stage 3 sends the bounded request to Ministral through LM Studio, records local provenance and externalTransmission=false, then persists the completed result and history.",
      "Do not present the CI screenshot as a capture of the real LM Studio Developer Logs. The screenshot comes from the publication workflow's contract double; the user separately completed the same candidate against the real local model and observed the request in LM Studio.",
      "The next slide explains why those visible sections remain distinct inside the application.",
      "03:45 including browser switch",
      "How do I know R5 reached the real local model?",
      "The publication workflow proved the packaged browser path and retained this screenshot. The WatchInfra rehearsal then sent the same candidate to the real Ministral process, appeared in LM Studio Developer Logs and returned the generated result to the browser.",
      ["GitHub PR #439", "GitHub issues #398 and #427", "R5 proof workflow 34020857953", "Artifact 9985493952", "Executable source f6b989af9574a8d54249e29ffff2045129d8f127", "docs/reviewer/screenshots/R5_lmstudio_ensemble_customer_444.png", "docs/reviewer/r5-runtime.md"]
    ),
  },
  {
    shapes: [
      ...titleBlock("Trust comes from separating detection, grounding and synthesis", "Application data flow"),
      rect(58, 190, 1164, 288, { fill: C.pale, line: C.line, width: 1, radius: 0 }),
      ...[
        [120, C.ink, "SOURCE", "Customer snapshot\nand source risk", "Database"],
        [290, C.red, "DETECT", "Derived signals\nwith provenance", "Radar"],
        [460, C.blue, "RETRIEVE", "Relevant synthetic\npolicy chunks", "BookOpenCheck"],
        [630, C.slate, "ENVELOPE", "Bounded typed\nevidence context", "PackageCheck"],
        [800, C.green, "SYNTHESIZE", "Structured result\nthrough one port", "Sparkles"],
        [970, C.amber, "VALIDATE", "Evidence references\nmust be supported", "ShieldCheck"],
        [1140, C.redDark, "PERSIST", "Completed result\nand history", "ArchiveRestore"],
      ].flatMap(([cx, color, h, b, asset], index, stages) => [
        ...(index < stages.length - 1 ? [line(cx + 38, 275, 94, 0, { color: C.line, width: 3, arrow: true })] : []),
        text(cx - 18, 206, 36, 20, String(index + 1).padStart(2, "0"), { size: 10, color, bold: true, align: "center" }),
        ...iconBadge(cx - 38, 236, 76, asset, color, C.white),
        text(cx - 72, 330, 144, 24, h, { size: 13, color, bold: true, tracking: 30, align: "center" }),
        text(cx - 72, 362, 144, 54, b, { size: 13, color: C.ink, align: "center", valign: "mid" }),
      ]),
      rect(682, 438, 516, 26, { fill: C.white, line: C.amber, width: 1, radius: 0 }),
      text(700, 442, 480, 18, "Any failure remains explicit before persistence", { size: 11, color: C.amber, bold: true, align: "center" }),
      rect(94, 516, 1092, 92, { fill: C.white, line: C.line, radius: 0 }),
      ...iconBadge(118, 536, 50, "Fingerprint", C.blue, C.blueSoft, true),
      text(188, 530, 410, 60, "Source facts stay authoritative and separate from derived evidence", { size: 16, color: C.ink, bold: true, valign: "mid" }),
      line(634, 530, 0, 60, { color: C.line, width: 2 }),
      ...iconBadge(670, 536, 50, "FileCheck2", C.green, C.greenSoft, true),
      text(740, 530, 410, 60, "Only a valid completed result reaches reviewable history", { size: 16, color: C.ink, bold: true, valign: "mid" }),
      ...footer(5),
    ],
    notes: note(
      "Each stage answers a different question and produces a different evidence type.",
      "Stage 1 produces advisory detector signals. Stage 2 retrieves contextual policy. The application assembles a bounded AnalysisEvidenceEnvelope. Stage 3 returns one provider-neutral structured result. A grounding validator rejects unsupported evidence references before persistence. Only a valid completed result reaches history.",
      "Do not describe detector scores as source risk truth. Do not describe policy retrieval as model training.",
      "These stages stay replaceable because the application owns the ports between them.",
      "00:50",
      "Why not send all transactions directly to one language model?",
      "The staged flow keeps source facts, derived signals, policy context and generated wording independently testable and attributable.",
      ["backend/src/main/java/dev/specgraph/reference/analysis/AnalysisService.java", "docs/assignment/SDD/SDD.md sections 6 and 9", "docs/assignment/ADR/ADR-002-provider-neutral-analysis.md", "Lucide icon library, ISC license"]
    ),
  },
  {
    shapes: [
      ...titleBlock("The analytical pipeline is a portfolio — not one model", "Implementation landscape"),
      text(60, 143, 300, 20, "STATUS · 6 SEPTEMBER 2026", { size: 10, color: C.muted, bold: true, tracking: 110 }),
      ellipse(718, 146, 12, 12, { fill: C.green, line: C.green }),
      text(738, 141, 82, 22, "DELIVERED", { size: 10, color: C.muted, bold: true }),
      ellipse(838, 146, 12, 12, { fill: C.amber, line: C.amber }),
      text(858, 141, 84, 22, "FOLLOW-UP", { size: 10, color: C.muted, bold: true }),
      ellipse(968, 146, 12, 12, { fill: C.white, line: C.slate, width: 2 }),
      text(988, 141, 112, 22, "PLANNED", { size: 10, color: C.muted, bold: true }),

      rect(58, 178, 570, 42, { fill: C.redSoft, line: C.red, width: 1.5, radius: 6 }),
      text(76, 186, 534, 26, "STAGE 1 · DETECT & SCORE", { size: 14, color: C.redDark, bold: true, tracking: 80 }),
      rect(72, 236, 542, 52, { fill: C.white, line: C.red, width: 2, radius: 8 }),
      icon(86, 247, 30, "Radar", C.redDark),
      text(130, 243, 286, 36, "RiskSignalDetectorPort", { size: 18, color: C.ink, bold: true, valign: "mid" }),
      text(438, 246, 154, 30, "stable contract", { size: 10, color: C.muted, bold: true, align: "right", valign: "mid" }),
      line(343, 288, 0, 18, { color: C.red, width: 2 }),
      line(131, 306, 406, 0, { color: C.red, width: 2 }),
      ...[131, 265, 399, 533].map((x) => line(x, 306, 0, 12, { color: C.red, width: 2 })),
      ...[
        [70, "Minus", "No-op", "baseline", C.green],
        [204, "CircleGauge", "Bayesian", "sequential", C.green],
        [338, "Activity", "Fuzzy logic", "graded rules", C.green],
        [472, "Trees", "Random Forest", "weighted ensemble\nof trees", C.green],
      ].flatMap(([x, asset, heading, detail, status]) => [
        rect(x, 318, 122, 96, { fill: C.white, line: status, width: status === C.amber ? 2 : 1.5, radius: 8 }),
        icon(x + 12, 334, 26, asset, heading === "Random Forest" ? C.amber : (status === C.amber ? C.amber : C.redDark)),
        ellipse(x + 101, 328, 10, 10, { fill: status, line: status }),
        text(x + 40, 328, 76, 34, heading, { size: heading === "Random Forest" ? 10 : 10.5, color: C.ink, bold: true, valign: "mid" }),
        text(x + 10, 370, 102, 34, detail, { size: heading === "Random Forest" ? 9 : 10, color: C.muted, align: "center", valign: "mid" }),
      ]),
      rect(72, 432, 542, 52, { fill: C.greenSoft, line: C.green, width: 1.5, radius: 8 }),
      icon(88, 443, 30, "GitMerge", C.green),
      text(132, 439, 136, 34, "COMPOSITE", { size: 13, color: C.green, bold: true, tracking: 70, valign: "mid", href: "https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/263" }),
      text(270, 439, 326, 34, "Ordered dispatch; every child artifact remains visible", { size: 12, color: C.ink, bold: true, valign: "mid" }),
      rect(72, 500, 542, 64, { fill: C.amberSoft, line: C.amber, width: 1.5, radius: 8 }),
      icon(88, 517, 30, "GitBranch", C.amber),
      line(343, 484, 0, 16, { color: C.amber, width: 2 }),
      text(132, 507, 244, 22, "CALIBRATED LATE FUSION", { size: 11, color: C.amber, bold: true, tracking: 30, href: "https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/254" }),
      text(132, 532, 462, 22, "Target: normalize semantics → weight → emit one additional artifact", { size: 10.5, color: C.ink, valign: "mid" }),

      rect(642, 178, 254, 42, { fill: C.blueSoft, line: C.blue, width: 1.5, radius: 6 }),
      text(660, 186, 218, 26, "STAGE 2 · GROUND", { size: 14, color: C.blue, bold: true, tracking: 80 }),
      rect(656, 236, 226, 52, { fill: C.white, line: C.blue, width: 2, radius: 8 }),
      icon(670, 247, 30, "BookOpenCheck", C.blue),
      text(714, 243, 150, 36, "PolicyKnowledgePort", { size: 10.5, color: C.ink, bold: true, valign: "mid" }),
      line(769, 288, 0, 12, { color: C.blue, width: 2 }),
      line(769, 300, -125, 0, { color: C.blue, width: 2 }),
      line(644, 300, 0, 154, { color: C.blue, width: 2 }),
      line(644, 351, 12, 0, { color: C.blue, width: 2 }),
      line(644, 454, 12, 0, { color: C.blue, width: 2 }),
      rect(656, 312, 226, 78, { fill: C.white, line: C.green, width: 1.5, radius: 8 }),
      icon(672, 330, 30, "FileText", C.green),
      ellipse(856, 322, 10, 10, { fill: C.green, line: C.green }),
      text(706, 321, 166, 26, "Static", { size: 13, color: C.ink, bold: true }),
      text(706, 350, 166, 24, "offline policy baseline", { size: 10.5, color: C.muted }),
      rect(656, 406, 226, 96, { fill: C.white, line: C.green, width: 1.5, radius: 8 }),
      icon(672, 430, 30, "Database", C.green),
      ellipse(856, 416, 10, 10, { fill: C.green, line: C.green }),
      text(706, 417, 166, 32, "pgvector + MiniLM", { size: 12, color: C.ink, bold: true }),
      text(706, 454, 166, 32, "local retrieval\nwith source metadata", { size: 10.5, color: C.muted }),
      rect(656, 518, 226, 46, { fill: C.blueSoft, line: C.blue, width: 1, radius: 6 }),
      text(672, 526, 194, 28, "PolicyEvidence + provenance", { size: 11, color: C.blue, bold: true, align: "center", valign: "mid" }),

      rect(910, 178, 312, 42, { fill: C.amberSoft, line: C.amber, width: 1.5, radius: 6 }),
      text(928, 186, 276, 26, "STAGE 3 · SYNTHESIZE", { size: 14, color: C.amber, bold: true, tracking: 80 }),
      rect(924, 236, 284, 52, { fill: C.white, line: C.amber, width: 2, radius: 8 }),
      icon(938, 247, 30, "BrainCircuit", C.amber),
      text(982, 243, 208, 36, "AnalysisModelPort", { size: 17, color: C.ink, bold: true, valign: "mid" }),
      line(1066, 288, 0, 12, { color: C.amber, width: 2 }),
      line(1066, 300, -154, 0, { color: C.amber, width: 2 }),
      line(912, 300, 0, 210, { color: C.amber, width: 2 }),
      line(912, 346, 12, 0, { color: C.amber, width: 2 }),
      line(912, 428, 12, 0, { color: C.amber, width: 2 }),
      line(912, 510, 12, 0, { color: C.amber, width: 2 }),
      ...[
        [312, "ShieldCheck", "Deterministic", "offline acceptance baseline", C.green, ""],
        [394, "CloudCog", "OpenAI", "explicit external opt-in", C.green, "https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/221"],
        [476, "LaptopMinimal", "LM Studio", "local R5 · delivered", C.green, "https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/439"],
      ].flatMap(([y, asset, heading, detail, status, href]) => [
        rect(924, y, 284, 68, { fill: C.white, line: status, width: 1.5, radius: 8 }),
        icon(940, y + 18, 30, asset, asset === "LaptopMinimal" ? C.slate : C.amber),
        ellipse(1182, y + 10, 10, 10, { fill: status === C.slate ? C.white : status, line: status, width: status === C.slate ? 2 : 1 }),
        text(984, y + 8, 182, 24, heading, { size: 14, color: C.ink, bold: true, href }),
        text(984, y + 34, 198, 22, detail, { size: 11, color: C.muted }),
      ]),
      rect(924, 558, 284, 38, { fill: C.greenSoft, line: C.green, width: 1.5, radius: 6 }),
      ellipse(940, 572, 10, 10, { fill: C.green, line: C.green }),
      text(958, 565, 232, 24, "Explicit backend selection · delivered", { size: 10, color: C.green, bold: true, valign: "mid", href: "https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/439" }),

      rect(72, 616, 1136, 38, { fill: C.ink, line: C.ink, radius: 6 }),
      text(94, 623, 1092, 24, "One AnalysisService · bounded evidence · provider-neutral output · same operator contract", { size: 12, color: C.white, bold: true, align: "center", valign: "mid" }),
      ...footer(6),
    ],
    notes: note(
      "The analytical pipeline supports several implementations without becoming several applications.",
      "Read the slide left to right. Stage 1 now delivers Bayesian, fuzzy and Random Forest evidence through the Composite while preserving every child artifact. The forest is itself a weighted ensemble of tree models; that differs from the heterogeneous Composite. Calibrated late fusion remains a follow-up. Stage 2 delivers pgvector plus local MiniLM grounding. Stage 3 retains deterministic and opt-in OpenAI implementations and now also delivers the local LM Studio adapter through explicit backend selection.",
      "Do not call the Composite a calibrated ensemble. Do not call the Random Forest vote share a calibrated probability. Do not claim that fusion improves accuracy before reproducible benchmark evidence exists.",
      "The next slide shows the ports that make this portfolio replaceable without moving application semantics.",
      "01:00",
      "Why introduce several analytical implementations in a five-day pilot?",
      "They test different uncertainty and failure assumptions behind one bounded contract. The stable port lets the team compare or replace them without duplicating orchestration, grounding, persistence or the operator journey.",
      [
        "docs/assignment/SDD/diagrams/hexagonal-architecture.svg",
        "https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/263",
        "https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/223",
        "https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/384",
        "https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/254",
        "https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/326",
        "https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/439",
        "RiskSignalDetectorPort, PolicyKnowledgePort and AnalysisModelPort",
      ]
    ),
  },
  {
    shapes: [
      ...titleBlock("Stable ports let adapters evolve without moving application semantics", "Hexagonal substitution map"),
      rect(88, 174, 1104, 170, { fill: C.ink, line: C.ink, radius: 16 }),
      text(120, 196, 1040, 30, "APPLICATION CORE", { size: 15, color: "FF8589", bold: true, tracking: 160, align: "center" }),
      ...iconBadge(130, 246, 62, "Boxes", C.white, "2B3742", true),
      text(222, 246, 870, 62, "Customer review and analysis use cases own the domain contracts, failure semantics and orchestration", { size: 21, color: C.white, bold: true, align: "center", valign: "mid" }),
      ...[147, 337, 527, 717, 907, 1097].map((x) => line(x, 344, 0, 72, { color: C.line, width: 2 })),
      ...[
        [60, "Operator\nContextPort", "Spring Security\nStatic baseline", "UserRoundCheck", C.red],
        [250, "Customer\nActivityPort", "Spring JDBC\nSynthetic baseline", "Database", C.blue],
        [440, "Risk Signal\nDetectorPort", "No-op · Bayesian · Fuzzy\nRandom Forest delivered", "Radar", C.redDark],
        [630, "Policy\nKnowledge\nPort", "pgvector + MiniLM\nStatic baseline", "BookOpenCheck", C.green],
        [820, "Analysis\nModelPort", "Deterministic\nProvider adapters", "BrainCircuit", C.amber],
        [1010, "Analysis\nHistoryPort", "Spring JDBC\nIn-memory baseline", "History", C.slate],
      ].flatMap(([x, port, adapters, asset, color]) => [
        rect(x, 416, 174, 70, { fill: C.redSoft, line: C.red, width: 2, radius: 8 }),
        ...iconBadge(x + 12, 431, 40, asset, color, C.white, true),
        text(x + 60, 426, 104, 50, port, { size: port.includes("Knowledge") ? 10 : 12, color: C.redDark, bold: true, align: "left", valign: "mid" }),
        rect(x, 504, 174, 96, { fill: C.white, line: C.line, width: 1, radius: 8 }),
        rect(x, 504, 174, 5, { fill: color, line: color, radius: 0 }),
        text(x + 10, 520, 154, 64, adapters, { size: 14, color: C.ink, align: "center", valign: "mid" }),
      ]),
      text(100, 624, 1080, 28, "Framework and provider types stop at the adapter boundary", { size: 17, color: C.muted, bold: true, align: "center" }),
      ...footer(7),
    ],
    notes: note(
      "The architecture spends complexity only at boundaries expected to change.",
      "The application core owns use cases and project values. Identity, activity storage, detection, retrieval, synthesis and history each connect through a project-owned port. The adapters can change independently while failure semantics and the operator contract stay stable.",
      "Do not present hexagonal architecture as a visual pattern or as proof of production readiness. Its value here is concrete substitution and test isolation.",
      "The delivery rings used those seams to replace capability incrementally.",
      "00:45",
      "Why was this architecture justified for a short exercise?",
      "Detector, retrieval and model choices change at different rates. Stable ports prevented those experiments from redefining the application contract.",
      ["docs/assignment/ADR/ADR-001-modular-monolith-hexagonal.md", "docs/assignment/ADR/ADR-002-provider-neutral-analysis.md", "docs/assignment/SDD/diagrams/hexagonal-architecture.puml", "Lucide icon library, ISC license"]
    ),
  },
  {
    shapes: [
      ...titleBlock("Six delivery rings kept one deployable shell reviewable throughout the build", "Concentric delivery"),
      ellipse(74, 170, 430, 430, { fill: "none", line: C.redDark, width: 3 }),
      ellipse(104, 200, 370, 370, { fill: "none", line: C.red, width: 3 }),
      ellipse(134, 230, 310, 310, { fill: "none", line: "C94D52", width: 2 }),
      ellipse(164, 260, 250, 250, { fill: "none", line: "977176", width: 2 }),
      ellipse(194, 290, 190, 190, { fill: "none", line: C.blue, width: 2 }),
      ellipse(234, 330, 110, 110, { fill: C.redSoft, line: C.ink, width: 2 }),
      text(255, 348, 68, 40, "R0", { size: 22, color: C.ink, bold: true, align: "center" }),
      text(255, 294, 68, 24, "R1", { size: 13, color: C.blue, bold: true, align: "center" }),
      text(255, 264, 68, 24, "R2", { size: 13, color: "977176", bold: true, align: "center" }),
      text(255, 234, 68, 24, "R3", { size: 13, color: "C94D52", bold: true, align: "center" }),
      text(255, 204, 68, 24, "R4", { size: 13, color: C.red, bold: true, align: "center" }),
      text(255, 174, 68, 24, "R5", { size: 13, color: C.redDark, bold: true, align: "center" }),
      ...[
        [570, 180, "R0", "Deployable hollow shell", "Same browser, API, ports and packaging"],
        [570, 248, "R1", "Synthetic customer review", "First meaningful operator path"],
        [570, 316, "R2", "Relational substitution", "PostgreSQL and source-shaped evidence"],
        [570, 384, "R3", "Analysis and history", "Deterministic end-to-end baseline"],
        [570, 452, "R4", "Authenticated retrieval", "R4 baseline retained"],
        [570, 520, "R5", "Full interview demonstrator", "Delivered composite + LM Studio path"],
      ].flatMap(([x, y, ring, h, b], i) => [
        text(x, y, 48, 30, ring, { size: 15, color: i >= 4 ? C.red : C.ink, bold: true }),
        line(x + 58, y + 14, 44, 0, { color: i >= 4 ? C.red : C.line, width: 2 }),
        text(x + 116, y - 2, 470, 30, h, { size: 17, color: C.ink, bold: true }),
        text(x + 116, y + 26, 470, 28, b, { size: 14, color: C.muted }),
      ]),
      ...footer(8),
    ],
    notes: note(
      "Every ring preserved a coherent demonstrable application.",
      "R0 established the real shell. R1 made the customer review meaningful. R2 changed storage. R3 added deterministic analysis and history. R4 added authentication and real retrieval. R5 delivers the full interview path with composite detector evidence and local Ministral synthesis through LM Studio. Rings describe capability maturity. J1 to J5 remain the calendar dimension.",
      "Do not present calibrated late fusion or production AML quality as delivered. The R5 runtime itself is delivered and frozen by PR #439.",
      "The same discipline governed the parallel work through GitHub.",
      "00:45",
      "Why keep earlier rings after R4 exists?",
      "They prove that adapters changed without replacing the shell and provide executable checkpoints for compatibility and demonstration fallback.",
      ["docs/assignment/SDD/diagrams/delivery-rings.dot", "docs/assignment/Inception/Inception.md section 18", "README.md section Delivery rings", "GitHub PR #439"]
    ),
  },
  {
    shapes: [
      ...titleBlock("GitHub mediated every hand-off between one accountable human and parallel agents", "Engineering control plane"),
      line(270, 332, 180, 0, { color: C.red, width: 3, arrow: "both" }),
      line(710, 282, 220, -52, { color: C.blue, width: 2, arrow: "both" }),
      line(710, 332, 220, 0, { color: C.blue, width: 2, arrow: "both" }),
      line(710, 382, 220, 52, { color: C.blue, width: 2, arrow: "both" }),
      ellipse(84, 242, 186, 186, { fill: C.redSoft, line: C.red, width: 3 }),
      icon(145, 266, 64, "UserRoundCheck", C.redDark),
      text(110, 338, 134, 30, "HUMAN", { size: 18, color: C.redDark, bold: true, align: "center" }),
      text(106, 374, 142, 28, "Prioritizes and accepts", { size: 13, color: C.ink, align: "center" }),
      ellipse(450, 202, 260, 260, { fill: C.ink, line: C.ink }),
      icon(538, 234, 84, "GitPullRequest", C.white),
      text(486, 326, 188, 26, "GITHUB", { size: 19, color: "FF8589", bold: true, align: "center" }),
      text(486, 358, 188, 44, "CONTROL PLANE", { size: 18, color: "FF8589", bold: true, align: "center" }),
      text(486, 410, 188, 20, "work state + evidence", { size: 12, color: "DDE3E8", align: "center" }),
      ...[
        [930, 180, "AGENT A", "bounded implementation"],
        [930, 287, "AGENT B", "independent review"],
        [930, 394, "AGENT C", "verification or docs"],
      ].flatMap(([x, y, h, b]) => [
        rect(x, y, 260, 82, { fill: C.blueSoft, line: C.blue, width: 2, radius: 10 }),
        ...iconBadge(x + 16, y + 16, 50, "Bot", C.blue, C.white, true),
        text(x + 82, y + 12, 158, 24, h, { size: 15, color: C.blue, bold: true }),
        text(x + 82, y + 42, 158, 28, b, { size: 13, color: C.ink }),
      ]),
      rect(330, 506, 560, 126, { fill: C.pale, line: C.line, radius: 10 }),
      ...iconBadge(352, 536, 56, "Workflow", C.red, C.white, true),
      text(432, 520, 420, 26, "NATIVE WORK GRAPH", { size: 13, color: C.ink, bold: true }),
      text(432, 550, 420, 34, "Issues, PRs, relations, milestones and lifecycle", { size: 14, color: C.muted }),
      line(432, 594, 420, 0, { color: C.line, width: 1 }),
      text(432, 602, 420, 22, "Delivery priority · Discovery disposition", { size: 13, color: C.redDark, bold: true }),
      text(916, 514, 290, 112, "The semantics can map to existing enterprise vocabulary. This exercise does not claim enterprise application integration.", { size: 14, color: C.muted, align: "center", valign: "mid" }),
      ...footer(9),
    ],
    notes: note(
      "GitHub supplied the shared state that neither chat transcripts nor one agent memory could provide.",
      "The human chose priorities, resolved trade-offs and accepted results. Agents received bounded work, published change and evidence, and reviewed each other through the same durable graph. Native GitHub relations carried ownership and dependency. Two typed Project fields added only the planning dimensions GitHub does not represent natively.",
      "Do not claim enterprise integration. Do not present labels or prose as lifecycle state. Do not imply that an agent can merge its own unreviewed result.",
      "The same graph can be projected for different management conversations.",
      "00:50",
      "Why use GitHub as the control plane instead of a custom agent dashboard?",
      "GitHub already owns review, code identity, issues, pull requests and most work relationships. Reusing it reduced custom machinery and kept the human in familiar controls.",
      ["AGENTS.md", "docs/assignment/Inception/Inception.md section 22", "https://github.com/jdoe-dev-159753/specgraph-harness/issues/28", "scripts/work_graph_guard.py", "Lucide icon library, ISC license"]
    ),
  },
  {
    shapes: [
      ...titleBlock("One work graph supports delivery, schedule and scope conversations", "Management projections"),
      text(930, 116, 290, 24, "SCHEMATIC PREVIEW", { size: 10, color: C.amber, bold: true, tracking: 120, align: "right" }),
      rect(58, 160, 548, 208, { fill: C.white, line: C.line, radius: 10 }),
      ...iconBadge(78, 174, 42, "Network", C.red, C.redSoft, true),
      text(136, 182, 220, 28, "WORKGRAPH", { size: 16, color: C.red, bold: true }),
      line(160, 260, 104, -36, { color: C.line, width: 2, arrow: "both" }),
      line(264, 224, 104, 62, { color: C.line, width: 2, arrow: "both" }),
      line(264, 224, 180, 0, { color: C.line, width: 2, arrow: "both" }),
      ...[[120,242],[232,206],[342,270],[430,206]].flatMap(([x,y],i)=>[
        ellipse(x, y, 52, 52, { fill: i===0?C.redSoft:C.blueSoft, line: i===0?C.red:C.blue, width: 2 }),
        text(x, y+15, 52, 22, i===0?"ROOT":`#${120+i}`, { size: 10, color: C.ink, bold: true, align:"center" }),
      ]),
      text(78, 328, 498, 24, "Hierarchy, dependencies and PR ownership", { size: 13, color: C.muted, align: "center" }),
      rect(674, 160, 548, 208, { fill: C.white, line: C.line, radius: 10 }),
      ...iconBadge(694, 174, 42, "Columns3", C.blue, C.blueSoft, true),
      text(752, 182, 220, 28, "DELIVERY", { size: 16, color: C.red, bold: true }),
      ...[[704,"TODO"],[864,"IN PROGRESS"],[1048,"DONE"]].flatMap(([x,h],i)=>[
        text(x, 220, i===1?160:130, 20, h, { size: 11, color: C.muted, bold: true, align:"center" }),
        rect(x, 248, i===1?160:130, 42, { fill: i===1?C.redSoft:C.pale, line:i===1?C.red:C.line, radius:6 }),
        rect(x, 300, i===1?160:130, 30, { fill:C.white, line:C.line, radius:6 }),
      ]),
      rect(58, 394, 548, 226, { fill: C.white, line: C.line, radius: 10 }),
      ...iconBadge(78, 408, 42, "CalendarRange", C.amber, "FFF7E8", true),
      text(136, 416, 260, 28, "MILESTONES", { size: 16, color: C.red, bold: true }),
      line(104, 470, 430, 0, { color: C.line, width: 2 }),
      ...[0,1,2,3,4].flatMap((i)=>[
        ellipse(116+i*96, 458, 24, 24, { fill:i<3?C.red:C.white, line:C.red, width:2 }),
        text(104+i*96, 492, 48, 20, `J${i+1}`, { size:11, color:C.muted, bold:true, align:"center" }),
      ]),
      rect(110, 536, 218, 22, { fill:C.blueSoft, line:C.blue, radius:6 }),
      rect(304, 570, 222, 22, { fill:C.redSoft, line:C.red, radius:6 }),
      rect(674, 394, 548, 226, { fill: C.white, line: C.line, radius: 10 }),
      ...iconBadge(694, 408, 42, "ChartNoAxesCombined", C.green, C.greenSoft, true),
      text(752, 416, 260, 28, "BURN-UP", { size: 16, color: C.red, bold: true }),
      line(724, 576, 0, -116, { color:C.muted, width:1 }),
      line(724, 576, 420, 0, { color:C.muted, width:1 }),
      line(724, 560, 84, -48, { color:C.blue, width:3 }),
      line(808, 512, 84, -8, { color:C.blue, width:3 }),
      line(892, 504, 84, -60, { color:C.blue, width:3 }),
      line(976, 444, 84, -22, { color:C.blue, width:3 }),
      line(724, 518, 84, -6, { color:C.red, width:2 }),
      line(808, 512, 84, -26, { color:C.red, width:2 }),
      line(892, 486, 84, -10, { color:C.red, width:2 }),
      line(976, 476, 84, -34, { color:C.red, width:2 }),
      text(704, 590, 454, 18, "completed work and evolving scope", { size:11, color:C.muted, align:"center" }),
      ...footer(10),
    ],
    notes: note(
      "The graph supports four views without creating four sources of truth.",
      "WorkGraph answers structure and dependency. Delivery answers current execution. Milestones answer time sequencing. Burn-up separates completed work from changing scope. These deliberately schematic previews explain the view semantics; native GitHub issue, pull-request and Project state remains authoritative.",
      "Do not present these four schematics as current screenshots. Do not infer priority from the Kanban or milestone dates.",
      "The next slide shows how deterministic feedback constrains the work before human acceptance.",
      "00:40",
      "Why keep multiple views if the underlying items are the same?",
      "Each view answers a different management question while preserving one underlying issue and pull-request graph.",
      ["AGENTS.md sections Delivery priority, Discovery disposition and Project status", "GitHub issue #49", "Private Project views shown as schematic context, not execution evidence", "Lucide icon library, ISC license"]
    ),
  },
  {
    shapes: [
      ...titleBlock("Fast feedback catches defects early and exact-head CI remains authoritative", "Agent quality feedback"),
      ...[224, 414, 604, 794, 984].map((x) => line(x, 324, 30, 0, { color:C.red, width:2, arrow:true })),
      ...[
        [44, C.ink, "BEFORE", "AGENTS.md policy\nand bounded context", "CURRENT", "BookOpenCheck"],
        [234, C.blue, "AFTER EDIT", "Earliest supported\nquality hook", "HARNESS ROADMAP", "Activity"],
        [424, C.blue, "PRE-COMMIT", "Shared lint and\ntype-check command", "HARNESS ROADMAP", "ListChecks"],
        [614, C.green, "PR CHECKS", "Design map, tests,\nsecurity and E2E", "CURRENT", "ShieldCheck"],
        [804, C.amber, "FROZEN HEAD", "One current-SHA\nCodex review", "CURRENT", "Fingerprint"],
        [994, C.red, "ACCEPT", "Human merge with\nexpected head SHA", "CURRENT", "BadgeCheck"],
      ].flatMap(([x,color,h,b,status,asset])=>[
        rect(x, 222, 180, 200, { fill:status==="CURRENT"?C.white:C.pale, line:color, width:status==="CURRENT"?2:1, radius:10 }),
        text(x+12, 238, 156, 22, h, { size:13, color, bold:true, align:"center" }),
        ...iconBadge(x+66, 270, 48, asset, color, C.white, true),
        text(x+12, 330, 156, 50, b, { size:13, color:C.ink, align:"center", valign:"mid" }),
        text(x+16, 394, 148, 18, status, { size:9, color:status==="CURRENT"?C.green:C.muted, bold:true, tracking:70, align:"center" }),
      ]),
      line(1084, 438, 0, 104, { color:C.muted, width:2 }),
      line(1084, 542, -760, 0, { color:C.muted, width:2 }),
      line(324, 542, 0, -104, { color:C.muted, width:2, arrow:true }),
      text(430, 486, 548, 48, "A failure returns the change to the shortest useful correction loop", { size:13,color:C.muted,bold:true,align:"center",valign:"mid",fill:C.white,margin:4 }),
      rect(174, 570, 932, 64, { fill:C.pale, line:C.line, radius:8 }),
      text(198, 585, 884, 34, "Hooks optimize latency. GitHub checks and the reviewed frozen SHA own merge authority.", { size:16, color:C.ink, bold:true, align:"center", valign:"mid" }),
      ...footer(11),
    ],
    notes: note(
      "The feedback hierarchy separates speed from authority.",
      "Repository instructions constrain generation before tool use. The harness roadmap experiments with the earliest supported post-edit hook and a shared pre-commit command. Those faster layers reduce correction latency but remain bypassable. The reference application already uses exact-head design, test, security, Playwright and work-graph gates. One fresh Codex review binds to the frozen head. The human accepts only that reviewed SHA.",
      "Do not present the post-edit or pre-commit harness experiments as landed here. Do not call a pre-use hook a type checker for code that does not exist yet.",
      "The next two slides quantify delivered scope and feedback-loop speed.",
      "00:50",
      "Why use both deterministic checks and an AI review?",
      "Checks own mechanically decidable rules. AI review focuses on ambiguity, architecture, counterexamples and missing evidence. The human reconciles findings and accepts the final head.",
      ["AGENTS.md section Freeze, validation, review and merge sequencing", ".github/workflows/application-ci.yml", ".github/workflows/work-graph-guard.yml", "https://github.com/jdoe-dev-159753/specgraph-harness/issues/74", "https://github.com/jdoe-dev-159753/specgraph-harness/issues/35", "Lucide icon library, ISC license"]
    ),
  },
  {
    shapes: [
      ...titleBlock("Replaying the observed workflow would require about 155 human workdays", "Measured delivery scale"),
      text(66, 166, 390, 126, "22×", { size: 88, color: C.red, bold: true, align: "center", valign: "mid" }),
      text(86, 286, 350, 56, "central replay compression", { size: 20, color: C.ink, bold: true, align: "center" }),
      text(96, 354, 330, 48, "155 workdays ÷ 7 calendar days = 22.1", { size: 14, color: C.muted, align: "center" }),
      line(500, 188, 0, 264, { color: C.line, width: 2 }),
      text(550, 164, 592, 26, "SELECTED METHOD · REPLAY THE OBSERVED EXECUTION", { size: 12, color: C.blue, bold: true, tracking: 90 }),
      text(550, 226, 170, 28, "Human alone", { size: 18, color: C.ink, bold: true }),
      rect(736, 222, 430, 40, { fill: C.pale, line: C.line, radius: 6 }),
      rect(736, 222, 430, 40, { fill: C.blue, line: C.blue, radius: 6 }),
      text(748, 230, 400, 24, "140–170 workdays · midpoint 155", { size: 13, color: C.white, bold: true, valign: "mid" }),
      text(550, 306, 170, 28, "Human + AI", { size: 18, color: C.ink, bold: true }),
      rect(736, 302, 430, 40, { fill: C.pale, line: C.line, radius: 6 }),
      rect(736, 302, 26, 40, { fill: C.red, line: C.red, radius: 6 }),
      text(778, 310, 350, 24, "7 calendar days observed", { size: 14, color: C.redDark, bold: true, valign: "mid" }),
      rect(540, 372, 642, 82, { fill: C.pale, line: C.line, radius: 6 }),
      text(554, 382, 614, 62, "What 115 meant: reproduce the final scope after the design is known. It excludes discarded paths, repeated reviews, failed-CI recovery and coordination, so it remains a lower bound.", { size: 12.5, color: C.muted, valign: "mid" }),
      line(66, 478, 1148, 0, { color: C.line, width: 1 }),
      ...[
        [66, "49,992", "changed lines in the complete corpus"],
        [354, "328", "files changed on main"],
        [642, "29,414", "net lines added on cited range"],
        [930, "62,578", "lines of cumulative PR churn"],
      ].flatMap(([x, value, label], index) => [
        ...(index ? [line(x - 22, 510, 0, 96, { color: C.line, width: 1 })] : []),
        text(x, 506, 244, 46, value, { size: 28, color: index === 0 ? C.red : C.ink, bold: true, align: "center" }),
        text(x, 558, 244, 48, label, { size: 13, color: C.muted, align: "center", valign: "mid" }),
      ]),
      rect(166, 620, 948, 34, { fill: C.pale, line: C.line, radius: 6 }),
      text(180, 626, 920, 22, "Parametric replay estimate, not a controlled productivity experiment", { size: 13, color: C.ink, bold: true, align: "center" }),
      ...footer(12),
    ],
    notes: note(
      "The selected comparison replays the observed PR, CI, review, correction and documentation workflow as human work.",
      "The earlier 115-day estimate answered a narrower counterfactual: how long one senior generalist might need to reproduce the final delivered scope after the architecture and solution path are already known. It removes discarded paths, repeated reviews, failed-CI recovery and coordination. The replay estimate retains those observed execution costs and therefore uses 140 to 170 human workdays, with 155 as the midpoint. Dividing 155 by seven gives 22.1, presented as 22 times.",
      "Do not claim a universally proven twenty-two-times productivity gain. There was no controlled human-only baseline, and the comparison mixes human workdays with seven calendar days of multi-agent execution and long operating hours.",
      "The next slide separates throughput from the speed of individual feedback loops.",
      "00:40",
      "Why use 155 rather than 115 human workdays?",
      "One hundred fifteen days estimates an optimized reproduction of the final scope. The selected 155-day midpoint instead prices the workflow that actually happened, including PR handling, CI and review cycles, corrections, documentation and delivery evidence. That matches the replay question more closely.",
      ["Git range 766ca0936e2ac544ed7d7e6393cba898284a0021..a2c3fd4cecdeb6f96e45b16af860520b355bb27e", "git diff --numstat: 29,823 additions - 409 deletions = 29,414 net lines", "GitHub PR and workflow snapshot, 6 September 2026", "Dynamic PR granularity model, 49,992 changed lines", "Human speed assumptions: 58 to 105 effective lines per hour by work class", "Replay range: 140 to 170 human workdays"]
    ),
  },
  {
    shapes: [
      ...titleBlock("AI assistance shortened reaction time between machine checks", "Feedback-loop velocity"),
      text(66, 154, 510, 28, "OBSERVED WITH AI ASSISTANCE", { size: 12, color: C.red, bold: true, tracking: 120 }),
      text(704, 154, 510, 28, "HUMAN ALONE, PARAMETRIC ESTIMATE", { size: 12, color: C.blue, bold: true, tracking: 90 }),
      line(640, 170, 0, 410, { color: C.line, width: 2 }),
      ...[
        [190, "PR opened to merged", "1.0 h", "Median   P75 7.5 h   P95 15.9 h", C.red, "BadgeCheck"],
        [286, "Failed CI to next attempt", "5.6 min", "Median   P75 13.5 min   P95 33.2 min", C.amber, "Activity"],
        [382, "Workflow execution", "1.25 min", "Median   success 4.0 min   failure 2.1 min", C.green, "ShieldCheck"],
        [478, "Issue opened to closed", "3.0 h", "Median   P75 11.3 h   P95 85.7 h", C.blue, "ListChecks"],
      ].flatMap(([y, label, value, detail, color, asset]) => [
        ...iconBadge(70, y, 54, asset, color, C.white, true),
        text(144, y - 2, 236, 24, label, { size: 15, color: C.ink, bold: true }),
        text(396, y - 4, 190, 30, value, { size: 20, color, bold: true, align: "right" }),
        text(144, y + 31, 442, 22, detail, { size: 11, color: C.muted, align: "right" }),
      ]),
      text(706, 202, 440, 26, "Exact workflow replay", { size: 17, color: C.ink, bold: true }),
      text(706, 238, 440, 64, "140 to 170 workdays\nCentral estimate: 155", { size: 20, color: C.blue, bold: true }),
      line(706, 316, 438, 0, { color: C.line, width: 1 }),
      text(706, 334, 440, 26, "Normalized output rate", { size: 17, color: C.ink, bold: true }),
      text(706, 366, 440, 34, "323 lines per workday", { size: 19, color: C.blue, bold: true }),
      text(706, 410, 440, 38, "Observed: 7,142 lines per calendar day", { size: 13, color: C.redDark, bold: true }),
      line(706, 462, 438, 0, { color: C.line, width: 1 }),
      text(706, 480, 440, 26, "Failure recovery estimate", { size: 17, color: C.ink, bold: true }),
      text(706, 514, 440, 66, "Human alone: 60 to 120 min\nObserved: 5.6 min", { size: 18, color: C.blue, bold: true }),
      rect(136, 606, 1008, 48, { fill: C.ink, line: C.ink, radius: 8 }),
      text(158, 617, 964, 26, "Acceleration occurred between checks: diagnosis, correction, next SHA", { size: 13, color: C.white, bold: true, align: "center", valign: "mid" }),
      ...footer(13),
    ],
    notes: note(
      "The strongest measured gain appears between a machine signal and the next attempted correction.",
      "Across the observed window, merged pull requests reached merge in one hour at the median. A failed workflow received another attempt on the same branch and workflow after 5.6 minutes at the median. The workflow itself remained mechanical, with a 1.25-minute median in the most recent 1,000-run sample. Human-only values are estimates: the user-supplied interruption model assigns 45 minutes of context recovery, then diagnosis and editing add roughly 15 to 75 minutes.",
      "Do not compare raw PR counts without accounting for their median 212 additions. Do not claim that AI accelerated the CI runner itself. Do not treat issue closure as proof of defect resolution quality.",
      "The conclusion frames these measurements as a basis for a controlled multi-team pilot.",
      "00:50",
      "Where did AI save time if CI still took the same time?",
      "The observed compression sits between checks: interpreting feedback, locating the relevant code, producing a correction and submitting the next SHA. Median failed-CI recovery was 5.6 minutes, while the human-only parameter range is 60 to 120 minutes.",
      ["GitHub PR snapshot: 97 merged PRs created since 30 August 2026", "GitHub issue snapshot: 306 issues closed", "GitHub Actions snapshot: 7,922 runs", "Most recent 1,000 workflow runs for duration percentiles", "116 failed-run follow-up pairs on the same branch and workflow", "Human-only recovery model: 45-minute context recovery plus diagnosis and edit time"]
    ),
  },
  {
    shapes: [
      ...titleBlock("The pilot validates a delivery cell while production scale remains the next test", "Conclusion"),
      text(72, 170, 330, 24, "CURRENTLY DEMONSTRATED", { size:12, color:C.red, bold:true, tracking:140 }),
      rect(70, 210, 420, 330, { fill:C.white, line:C.red, width:3, radius:14 }),
      ellipse(110, 258, 92, 92, { fill:C.redSoft, line:C.red, width:2 }),
      icon(134, 276, 44, "UserRoundCheck", C.redDark),
      text(110, 354, 92, 22, "HUMAN", { size:12, color:C.redDark, bold:true, align:"center" }),
      ellipse(242, 258, 92, 92, { fill:C.ink, line:C.ink }),
      icon(266, 276, 44, "GitPullRequest", C.white),
      text(242, 354, 92, 22, "GITHUB", { size:12, color:C.ink, bold:true, align:"center" }),
      ellipse(374, 258, 78, 78, { fill:C.blueSoft, line:C.blue, width:2 }),
      icon(394, 276, 38, "Bot", C.blue),
      text(374, 354, 78, 22, "AGENTS", { size:12, color:C.blue, bold:true, align:"center" }),
      line(202, 304, 40, 0, { color:C.red, width:2, arrow:"both" }),
      line(334, 304, 40, 0, { color:C.blue, width:2, arrow:"both" }),
      text(104, 374, 352, 92, "One accountable engineer coordinates bounded work and accepts evidence-backed change", { size:16, color:C.ink, bold:true, align:"center", valign:"mid" }),
      text(104, 482, 352, 34, "One operator retains the final review decision", { size:14, color:C.muted, align:"center" }),
      line(546, 372, 124, 0, { color:C.red, width:4, arrow:true }),
      text(690, 170, 470, 24, "NEXT PILOT HYPOTHESIS", { size:12, color:C.blue, bold:true, tracking:140 }),
      ...[
        [690, 218, "CELL A", "Customer Care", "UserRoundCheck"],
        [690, 316, "CELL B", "Another product team", "Boxes"],
        [690, 414, "CELL C", "Another engineer", "LaptopMinimal"],
      ].flatMap(([x,y,h,b,asset])=>[
        rect(x,y,230,76,{fill:C.blueSoft,line:C.blue,width:2,radius:10}),
        ...iconBadge(x+14,y+14,48,asset,C.blue,C.white,true),
        text(x+72,y+10,144,22,h,{size:13,color:C.blue,bold:true}),
        text(x+72,y+38,144,28,b,{size:12,color:C.ink}),
        line(x+230,y+38,70,0,{color:C.blue,width:2,arrow:"both"}),
      ]),
      rect(990, 230, 216, 250, { fill:C.ink, line:C.ink, radius:14 }),
      icon(1062, 248, 72, "Workflow", C.white),
      text(1000, 322, 196, 24, "SHARED", { size:16,color:C.white,bold:true,align:"center" }),
      text(1000, 350, 196, 28, "CONTROL PLANE", { size:16,color:C.white,bold:true,align:"center" }),
      line(1018, 390, 160, 0, { color:"667984", width:1 }),
      text(1000, 400, 196, 20, "Enterprise process fit", { size:10,color:"DDE3E8",align:"center" }),
      text(1000, 428, 196, 20, "Operational scale and HA", { size:10,color:"DDE3E8",align:"center" }),
      text(1000, 456, 196, 18, "Measured model quality", { size:10,color:"DDE3E8",align:"center" }),
      rect(164, 582, 952, 58, { fill:C.pale, line:C.line, radius:8 }),
      text(188, 596, 904, 32, "The next decision should be based on measured reuse, quality and operating cost across several real delivery cells", { size:15, color:C.ink, bold:true, align:"center", valign:"mid" }),
      ...footer(14),
    ],
    notes: note(
      "The credible conclusion is a scale experiment, not a production claim.",
      "This pilot demonstrates one complete delivery cell: an accountable engineer, bounded agents, a GitHub control plane, deterministic acceptance and one reviewable product path. The next test is whether the same method improves several real teams while fitting Swissquote process vocabulary, operational constraints and quality expectations.",
      "Do not claim production AML performance, regulatory adequacy, enterprise integration, high availability or measured productivity improvement.",
      "Invite questions and use the appendix for mechanism-level drill-down.",
      "00:35",
      "What would you test before scaling this role?",
      "Measure delivery throughput, escaped defects, review effort, model quality and operating cost across several bounded product increments, then adapt the control plane to existing enterprise systems.",
      ["docs/assignment/Inception/Inception.md success criterion", "GitHub issue #229 audience and narrative", "GitHub issue #74", "GitHub issue #250", "Lucide icon library, ISC license"]
    ),
  },
  {
    appendix: true,
    shapes: [
      ...titleBlock("Authentic Playwright captures document the capability growth from R1 to R5", "Appendix A · Browser evidence"),
      ...[
        [60, "R1", "Synthetic review", "R1_strip.png", C.ink],
        [350, "R2", "PostgreSQL", "R2_strip.png", C.blue],
        [640, "R3", "Analysis + history", "R3_strip.png", C.green],
        [930, "R4", "Auth + retrieval", "R4_strip.png", C.red],
      ].flatMap(([x,ring,label,file,color])=>[
        rect(x,166,276,174,{fill:C.white,line:C.line,width:1,radius:0}),
        image(x+6,172,264,162,path.join(screenshotDir,file)),
        text(x+12,178,44,26,ring,{size:12,color:C.white,bold:true,fill:color,align:"center",valign:"mid"}),
        text(x+62,178,196,26,label,{size:11,color:C.white,bold:true,fill:color,margin:5,valign:"mid"}),
      ]),
      rect(60,366,1160,270,{fill:C.white,line:C.red,width:2,radius:0}),
      image(68,374,566,254,path.join(screenshotDir,"R5_analysis_landscape.png")),
      image(646,374,566,254,path.join(screenshotDir,"R5_history_landscape.png")),
      text(76,384,52,30,"R5",{size:14,color:C.white,bold:true,fill:C.red,align:"center",valign:"mid"}),
      text(136,384,314,30,"FULL LM STUDIO DEMONSTRATOR",{size:11,color:C.white,bold:true,fill:C.red,margin:6,valign:"mid"}),
      text(1012,384,188,30,"DELIVERED",{size:11,color:C.white,bold:true,fill:C.green,align:"center",valign:"mid"}),
      ...footer("A", true),
    ],
    notes: note(
      "These are crops from authentic Playwright screenshots, not reconstructed mockups.",
      "R1 shows the synthetic customer review. R2 shows storage substitution through the same visible contract. R3 adds deterministic analysis and history. R4 adds authenticated operator identity and real pgvector retrieval. The large R5 panel shows the completed local-model analysis, Stage 3 local provenance and persisted history. The R1-to-R3 source PNGs remain in the named Actions artifact; the manifest identifies and digests that artifact. Only the explicitly promoted R4 and R5 source PNGs are repository-owned.",
      "Do not treat image recency as newer than the source SHA in the manifest. Do not present these crops as GitHub Project views.",
      "Return to the relevant main slide or continue to the architecture appendix.",
      "As needed",
      "Why trust these screenshots?",
      "The workflows assert the ring-specific behavior before retaining the Playwright artifact, and the manifest records run, artifact, digest, customer and source revision.",
      ["docs/reviewer/screenshot-manifest.md", "R1/R2/R3 artifact 9844165175", "R4 artifact 9856114241", "R5 proof workflow 34020857953", "R5 artifact 9985493952", "docs/reviewer/screenshots/R5_lmstudio_ensemble_customer_444.png"]
    ),
  },
  {
    appendix: true,
    shapes: [
      ...titleBlock("Eight ADRs concentrate the durable choices and their trade-offs", "Appendix B · Decision record"),
      ...[
        [60, 164, "ADR-001", "Modular monolith and strict hexagonal boundaries", "ADR-001-modular-monolith-hexagonal.md"],
        [60, 254, "ADR-002", "Provider-neutral analysis and trust boundary", "ADR-002-provider-neutral-analysis.md"],
        [60, 344, "ADR-003", "PostgreSQL and pgvector as one persistent store", "ADR-003-postgresql-pgvector-persistence.md"],
        [60, 434, "ADR-004", "Preferred Java, Spring and React stack", "ADR-004-baseline-web-stack.md"],
        [660, 164, "ADR-005", "One prebuilt application image per checkpoint", "ADR-005-prebuilt-demo-container-packaging.md"],
        [660, 254, "ADR-006", "Compose OCI and multi-platform distribution", "ADR-006-compose-oci-multi-platform-distribution.md"],
        [660, 344, "ADR-007", "Explicit relational adapters with Spring JDBC", "ADR-007-spring-jdbc-relational-adapters.md"],
        [660, 434, "ADR-008", "Customer Activity Analytics product identity", "ADR-008-customer-activity-analytics-identity.md"],
      ].flatMap(([x,y,id,label,file])=>[
        text(x,y,112,32,id,{size:15,color:C.red,bold:true,underline:true,href:`https://github.com/jdoe-dev-159753/specgraph-reference-app/blob/main/docs/assignment/ADR/${file}`}),
        text(x+120,y,460,54,label,{size:16,color:C.ink,bold:true}),
        line(x,y+66,548,0,{color:C.line,width:1}),
      ]),
      rect(92, 548, 1096, 76, { fill:C.pale,line:C.line,radius:8 }),
      text(112, 562, 1056, 46, "https://github.com/jdoe-dev-159753/specgraph-reference-app/tree/main/docs/assignment/ADR", { size:13,color:C.blue,bold:true,align:"center",valign:"mid",underline:true,href:"https://github.com/jdoe-dev-159753/specgraph-reference-app/tree/main/docs/assignment/ADR" }),
      ...footer("B", true),
    ],
    notes: note(
      "ADRs preserve the why behind choices that would otherwise look arbitrary.",
      "Open the individual links when a reviewer challenges a trade-off. The ADRs cover module shape, AI trust boundaries, persistence, stack reuse, packaging, distribution, relational access and reviewer-facing product identity. They record rejected alternatives and consequences rather than repeating the SDD.",
      "Do not treat ADRs as current execution evidence. Tests, code and workflow results own current behavior.",
      "Use the submodel slides only for mechanism-level questions.",
      "As needed",
      "Why eight ADRs for a five-day exercise?",
      "Only choices with durable alternatives received an ADR. The records make substitution and later review cheaper without turning every implementation detail into architecture.",
      ["docs/assignment/ADR/", "docs/assignment/SDD/SDD.md section 13"]
    ),
  },
  {
    appendix: true,
    shapes: [
      ...titleBlock("Three elevated observations shift the posterior toward higher review rates", "Appendix C · Beta-binomial detector"),
      text(74, 148, 500, 24, "Illustrative test case: 3 elevated observations out of 4", { size:13,color:C.ink,bold:true }),
      text(724, 148, 482, 24, "Beta(1, 4) updated to Beta(4, 5)", { size:13,color:C.redDark,bold:true,align:"right" }),
      ...betaDistributionPlot(60, 174, 1160, 392),
      rect(92, 582, 1096, 60, { fill:C.pale,line:C.line,width:1,radius:0 }),
      text(118, 594, 1044, 34, "The shaded tail is P(p > 0.40), not a probability of fraud or money laundering", { size:14,color:C.redDark,bold:true,align:"center",valign:"mid" }),
      ...footer("C", true),
    ],
    notes: note(
      "The plot shows how evidence changes the whole distribution, not only the final scalar.",
      "The blue curve is the explicit Beta one-four prior. This test case maps three of four activities to review-elevated observations, producing Beta four-five. The red posterior shifts its mean from 0.20 to 0.44. The shaded area above the fixed 0.40 reference equals 0.594 and becomes the emitted signal.",
      "Do not call the result a probability of fraud or money laundering. Do not claim production calibration.",
      "The fuzzy alternative expresses graded thresholds instead of a binary observation mapping.",
      "00:60 if asked",
      "What does the Bayesian score mean?",
      "It means the posterior probability that this synthetic review-elevated observation rate exceeds the fixed 0.40 reference, under the explicit Beta prior and mapping.",
      ["backend/src/main/java/dev/specgraph/reference/analysis/BayesianSequentialRiskSignalDetectorAdapter.java", "backend/src/test/java/dev/specgraph/reference/analysis/BayesianSequentialRiskSignalDetectorAdapterTests.java", "https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.beta.html"]
    ),
  },
  {
    appendix: true,
    shapes: [
      ...titleBlock("One overlapping partition turns prior-adjusted ratios into graded activation", "Appendix D · Fuzzy inference"),
      ...fuzzyPartitionChart(62, 170, 1156, 184),
      ...[
        [62, C.red, "CRYPTO", "weight 0.10"],
        [354, C.amber, "INCOMPLETE", "weight 0.10"],
        [646, C.blue, "CROSS-BORDER", "weight 0.2625"],
        [938, C.green, "SOURCE RISK", "weight 0.4875"],
      ].flatMap(([x, color, label, weight]) => [
        rect(x, 382, 254, 64, { fill:C.white, line:color, width:2, radius:6 }),
        text(x + 14, 392, 226, 22, label, { size:13, color, bold:true, align:"center" }),
        text(x + 14, 418, 226, 18, weight, { size:11, color:C.muted, align:"center" }),
      ]),
      rect(62, 472, 500, 166, { fill:C.pale, line:C.line, width:1, radius:6 }),
      text(82, 486, 460, 22, "1 · PRIOR-ADJUSTED FEATURE", { size:11, color:C.redDark, bold:true, tracking:60 }),
      text(82, 514, 460, 30, "r_eff = positives / (observations + 2)", { size:15, color:C.ink, bold:true }),
      text(82, 548, 460, 24, "2 · ACTIVATION", { size:11, color:C.redDark, bold:true, tracking:60 }),
      text(82, 576, 460, 28, "degree = 0.5 × μmedium + μhigh", { size:15, color:C.ink, bold:true }),
      text(82, 610, 460, 18, "Add-two zero-positive prior · thresholds 0.10 / 0.30 / 0.60", { size:10.5, color:C.muted }),
      rect(586, 472, 632, 166, { fill:C.white, line:C.line, width:1, radius:6 }),
      text(606, 486, 592, 22, "3 · FIXED MONOTONE SURFACE", { size:11, color:C.redDark, bold:true, tracking:60 }),
      text(606, 516, 592, 58, "score = clamp₀₋₁(0.05 + 0.10·crypto + 0.10·incomplete\n+ 0.2625·cross-border + 0.4875·source-risk)", { size:13.5, color:C.ink, bold:true }),
      text(606, 580, 592, 24, "Coupled min(cross-border, source-risk) is retained for diagnosis", { size:11, color:C.muted }),
      text(606, 608, 592, 18, "Its consequent weight is 0: no double counting · not an AML probability", { size:10.5, color:C.redDark, bold:true }),
      ...footer("D", true),
    ],
    notes: note(
      "The delivered v3 detector applies one overlapping low-medium-high partition to four prior-adjusted feature ratios.",
      "Each effective ratio divides its positive count by the observation count plus two zero-positive prior observations. The shared partition is low through 0.10, peaks at medium at 0.30 and reaches high at 0.60. Activation equals one half of the medium membership plus the high membership. The final bounded surface adds a 0.05 baseline and fixed weights of 0.10, 0.10, 0.2625 and 0.4875 for crypto, incomplete, cross-border and source-risk activation. The coupled minimum remains visible in provenance with zero consequent weight so the same evidence is not counted twice.",
      "Do not call fuzzy membership or the final score a statistical probability. Do not claim these synthetic thresholds are institutional policy or calibrated AML parameters.",
      "The next conditional slide explains how different detector families could be arbitrated after their semantics become comparable.",
      "00:60 if asked",
      "Why add two observations and use overlapping memberships?",
      "The add-two prior prevents tiny samples from saturating the detector, while the partition makes changes continuous and inspectable. Both are versioned heuristic choices, not learned or institutionally validated parameters.",
      ["backend/src/main/java/dev/specgraph/reference/analysis/FuzzyRiskSignalDetectorAdapter.java", "backend/src/test/java/dev/specgraph/reference/analysis/FuzzyRiskSignalDetectorAdapterTests.java"]
    ),
  },
  {
    appendix: true,
    shapes: [
      ...titleBlock("Late fusion requires comparable semantics before signals can be combined", "Appendix E · Conditional design target"),
      text(944, 112, 278, 28, "R5 · NOT YET MEASURED", { size:11,color:C.red,bold:true,tracking:100,align:"right" }),
      ...[
        [66, 188, C.ink, "DETERMINISTIC", "source-shaped baseline", "ListChecks"],
        [66, 276, C.blue, "BAYESIAN", "small-sample posterior", "CircleGauge"],
        [66, 364, C.amber, "FUZZY", "graded rule activation", "Activity"],
        [66, 452, C.green, "RANDOM FOREST", "learned tree votes", "Trees"],
      ].flatMap(([x,y,color,h,b,asset])=>[
        rect(x,y,270,64,{fill:C.white,line:color,width:2,radius:0}),
        rect(x,y,8,64,{fill:color,line:color,radius:0}),
        ...iconBadge(x+18,y+11,42,asset,color,C.pale,true),
        text(x+74,y+8,174,20,h,{size:13,color,bold:true}),
        text(x+74,y+34,174,22,b,{size:12,color:C.muted}),
        line(x+270,y+32,114,0,{color,width:2}),
      ]),
      line(450, 220, 0, 264, { color:C.slate,width:3 }),
      line(450, 352, 58, 0, { color:C.slate,width:3,arrow:true }),
      rect(508, 272, 218, 160, { fill:C.pale,line:C.slate,width:2,radius:0 }),
      ...iconBadge(589, 286, 56, "Scale", C.slate, C.white, true),
      text(530, 348, 174, 42, "SEMANTIC\nALIGNMENT", { size:14,color:C.ink,bold:true,align:"center" }),
      text(530, 402, 174, 18, "aligned target and scale", { size:10,color:C.muted,align:"center" }),
      line(726, 352, 70, 0, { color:C.red,width:3,arrow:true }),
      rect(796, 272, 190, 160, { fill:C.redSoft,line:C.red,width:3,radius:0 }),
      ...iconBadge(863, 288, 56, "GitMerge", C.redDark, C.white, true),
      text(818, 348, 146, 44, "LATE\nFUSION", { size:15,color:C.redDark,bold:true,align:"center" }),
      text(818, 402, 146, 18, "weighting and arbitration", { size:10,color:C.ink,align:"center" }),
      line(986, 352, 64, 0, { color:C.red,width:3,arrow:true }),
      rect(1050, 292, 172, 120, { fill:C.ink,line:C.ink,width:2,radius:0 }),
      icon(1112, 306, 48, "BadgeCheck", C.white),
      text(1070, 358, 132, 24, "ONE SIGNAL", { size:15,color:C.white,bold:true,align:"center" }),
      text(1070, 386, 132, 18, "evidence retained", { size:10,color:"DDE3E8",align:"center" }),
      rect(332, 548, 874, 78, { fill:C.blueSoft,line:C.blue,width:1,radius:0 }),
      text(360, 562, 818, 48, "Hypothesis: complementary blind spots may improve robustness. A frozen benchmark must measure that gain.", { size:15,color:C.ink,bold:true,align:"center",valign:"mid" }),
      ...footer("E", true),
    ],
    notes: note(
      "Late fusion is a future arbitration stage, not the current Composite behavior.",
      "Each detector specializes in a different view of the same bounded facts. After score semantics and calibration become comparable, a late-fusion stage could weight or arbitrate those signals. The fused output should retain the child evidence so disagreement and provenance remain inspectable. The expected benefit is complementary error behavior, but this project has not yet measured an accuracy or generalization gain.",
      "Do not present current Composite execution as calibrated fusion. Do not claim accuracy improvement without a frozen benchmark.",
      "The forest slide explains one candidate specialist.",
      "00:55 if asked",
      "Why combine several weak detectors?",
      "Specialists can make different errors. Fusion is useful only if comparable semantics and evaluation show that those differences improve the final signal.",
      ["GitHub issue #224", "GitHub issue #254", "GitHub issue #268", "Lucide icon library, ISC license"]
    ),
  },
  {
    appendix: true,
    shapes: [
      ...titleBlock("A Random Forest averages many bounded tree votes behind the same detector port", "Appendix F · Delivered R5 detector"),
      text(944, 112, 278, 28, "R5 · DELIVERED", { size:11,color:C.green,bold:true,tracking:100,align:"right" }),
      ...[
        [86, 214, C.blue, "cross-border ratio > t₁", "crypto ratio > t₂", "0", "1"],
        [426, 214, C.amber, "incomplete ratio > t₃", "activity volume > t₄", "0", "1"],
        [766, 214, C.green, "crypto ratio > t₅", "cross-border ratio > t₆", "0", "1"],
      ].flatMap(([x,y,color,root,child,lVote,rVote])=>[
        line(x+130,y+52,-78,72,{color,width:2,arrow:true}),
        line(x+130,y+52,78,72,{color,width:2,arrow:true}),
        line(x+52,y+154,-34,56,{color,width:2,arrow:true}),
        line(x+52,y+154,34,56,{color,width:2,arrow:true}),
        rect(x,y,260,54,{fill:C.white,line:color,width:2,radius:8}),
        text(x+10,y+14,240,26,root,{size:13,color:C.ink,bold:true,align:"center"}),
        rect(x-20,y+124,144,50,{fill:C.pale,line:color,width:1,radius:8}),
        text(x-12,y+137,128,24,child,{size:11,color:C.ink,bold:true,align:"center"}),
        ellipse(x-18,y+208,64,64,{fill:C.white,line:color,width:2}),
        text(x-18,y+227,64,24,lVote,{size:16,color,bold:true,align:"center"}),
        ellipse(x+58,y+208,64,64,{fill:color,line:color,width:2}),
        text(x+58,y+227,64,24,rVote,{size:16,color:C.white,bold:true,align:"center"}),
        ellipse(x+176,y+124,64,64,{fill:color,line:color,width:2}),
        text(x+176,y+143,64,24,rVote,{size:16,color:C.white,bold:true,align:"center"}),
      ]),
      ...[216,556,896].map((x)=>line(x,486,0,54,{color:C.red,width:2,arrow:true})),
      rect(176, 540, 840, 72, { fill:C.ink,line:C.ink,radius:12 }),
      text(204, 554, 784, 44, "Forest output = mean of tree votes for REVIEW_ELEVATED", { size:18,color:C.white,bold:true,align:"center",valign:"mid" }),
      text(176, 624, 840, 24, "Vote share, not a calibrated probability · synthetic training only", { size:14,color:C.redDark,bold:true,align:"center" }),
      ...footer("F", true),
    ],
    notes: note(
      "The forest visual shows actual trees and the runtime aggregation semantics.",
      "Each tree splits the bounded feature vector and emits one class vote. The runtime model averages votes for REVIEW_ELEVATED. Training is offline with a fixed seed and packaged model provenance. The adapter remains behind RiskSignalDetectorPort, so downstream grounding and synthesis do not know Tribuo types.",
      "Do not call the vote share a calibrated probability. Do not claim production AML validity from synthetic training.",
      "Return to the fusion target only if the reviewer asks how this specialist would combine with others.",
      "00:55 if asked",
      "Why Random Forest after Bayesian and fuzzy detectors?",
      "It adds a learned non-neural baseline with different inductive assumptions while keeping the same bounded features and evidence contract.",
      ["GitHub issue #223", "GitHub PR #439", "RiskSignalDetectorPort", "RandomForestRiskSignalDetectorAdapter"]
    ),
  },
  {
    appendix: true,
    shapes: [
      ...titleBlock("Stage 3 backend choice changes execution while preserving context and output contracts", "Appendix G · Backend data flow"),
      text(944, 112, 278, 28, "R5 · DELIVERED", { size:11,color:C.green,bold:true,tracking:100,align:"right" }),
      line(270, 342, 92, 0, { color:C.red,width:3,arrow:true }),
      line(570, 342, 82, -106, { color:C.green,width:2,arrow:true }),
      line(570, 342, 82, 0, { color:C.blue,width:2,arrow:true }),
      line(570, 342, 82, 106, { color:C.amber,width:2,arrow:true }),
      line(902, 236, 70, 106, { color:C.green,width:2,arrow:true }),
      line(902, 342, 70, 0, { color:C.blue,width:2,arrow:true }),
      line(902, 448, 70, -106, { color:C.amber,width:2,arrow:true }),
      rect(60, 262, 210, 160, { fill:C.pale,line:C.ink,width:2,radius:12 }),
      ...iconBadge(134, 278, 62, "PackageCheck", C.ink, C.white, true),
      text(80, 348, 170, 42, "EVIDENCE\nENVELOPE", { size:14,color:C.ink,bold:true,align:"center" }),
      text(80, 398, 170, 16, "source, detector, policy", { size:9,color:C.muted,align:"center" }),
      rect(362, 262, 208, 160, { fill:C.redSoft,line:C.red,width:2,radius:12 }),
      ...iconBadge(435, 278, 62, "ServerCog", C.redDark, C.white, true),
      text(382, 350, 168, 28, "TYPED FACTORY", { size:14,color:C.redDark,bold:true,align:"center" }),
      text(382, 386, 168, 28, "identity and capability checks", { size:10,color:C.ink,align:"center" }),
      ...[
        [652, 196, C.green, "DETERMINISTIC", "landed baseline", "ListChecks"],
        [652, 302, C.blue, "OPENAI", "optional adapter", "CloudCog"],
        [652, 408, C.amber, "LM STUDIO", "delivered local R5", "LaptopMinimal"],
      ].flatMap(([x,y,color,h,b,asset])=>[
        rect(x,y,250,80,{fill:C.white,line:color,width:2,radius:10}),
        ...iconBadge(x+14,y+15,50,asset,color,C.pale,true),
        text(x+80,y+10,152,24,h,{size:14,color,bold:true}),
        text(x+80,y+42,152,22,b,{size:12,color:C.muted}),
      ]),
      rect(972, 262, 250, 160, { fill:C.ink,line:C.ink,radius:12 }),
      icon(1071, 278, 52, "FileCheck2", C.white),
      text(994, 340, 206, 28, "ONE OUTPUT", { size:16,color:C.white,bold:true,align:"center" }),
      text(994, 378, 206, 28, "severity, findings and provenance", { size:11,color:"DDE3E8",align:"center" }),
      line(1097, 422, 0, 66, { color:C.red,width:3,arrow:true }),
      rect(934, 488, 326, 100, { fill:C.blueSoft,line:C.blue,width:2,radius:10 }),
      ...iconBadge(954, 512, 52, "ShieldCheck", C.blue, C.white, true),
      text(1024, 506, 210, 62, "Validated output\npersisted to history", { size:14,color:C.ink,bold:true,align:"center",valign:"mid" }),
      text(108, 610, 800, 34, "Default path sends no customer or policy content outside the local system", { size:15,color:C.muted,bold:true }),
      ...footer("G", true),
    ],
    notes: note(
      "Stage 3 selection is a factory concern, not application semantics.",
      "The bounded envelope enters one typed selection point. Deterministic execution remains the mandatory baseline. OpenAI remains opt-in. R5 delivers LM Studio as the local substitution. Every adapter returns the same structured result and model provenance. The grounding validator and history contract remain unchanged.",
      "Do not imply that local execution proves model quality. The delivered claim covers runtime selection, bounded structured exchange, provenance and the browser-visible result.",
      "The final appendix slide maps behaviors to the evidence level that proves them.",
      "00:55 if asked",
      "Why retain the deterministic backend after adding a live model?",
      "It keeps mandatory verification reproducible, isolates retrieval and orchestration failures, and provides a supported fallback without changing the operator workflow.",
      ["AnalysisModelPort", "AnalysisEvidenceEnvelope", "GitHub PR #439", "GitHub issue #251", "docs/reviewer/r5-runtime.md", "Lucide icon library, ISC license"]
    ),
  },
  {
    appendix: true,
    shapes: [
      ...titleBlock("Each behavior maps to the highest boundary that can prove it", "Appendix H · Verification evidence"),
      text(70, 164, 430, 22, "REVIEWER-VISIBLE BEHAVIOR", { size:11,color:C.muted,bold:true,tracking:90 }),
      text(720, 164, 490, 22, "EVIDENCE BOUNDARY", { size:11,color:C.muted,bold:true,tracking:90 }),
      ...[
        [204, C.red, "Operator can log in and reopen history", "Playwright against the packaged topology", "UserRoundCheck", "PanelTop"],
        [300, C.green, "Retrieved policy chunks remain attributable", "PostgreSQL and pgvector integration", "BookOpenCheck", "Database"],
        [396, C.blue, "Adapters preserve the application contract", "Port contract tests across substitutions", "Boxes", "TestTubeDiagonal"],
        [492, C.ink, "Scores remain bounded and repeatable", "Unit and property tests", "CircleGauge", "ListChecks"],
      ].flatMap(([y,color,behavior,evidence,leftAsset,rightAsset])=>[
        rect(70,y,430,64,{fill:C.white,line:color,width:2,radius:0}),
        rect(70,y,8,64,{fill:color,line:color,radius:0}),
        ...iconBadge(92,y+10,44,leftAsset,color,C.pale,true),
        text(152,y+12,324,40,behavior,{size:14,color:C.ink,bold:true,valign:"mid"}),
        line(500,y+32,220,0,{color,width:3,arrow:true}),
        rect(720,y,490,64,{fill:C.pale,line:color,width:2,radius:0}),
        ...iconBadge(742,y+10,44,rightAsset,color,C.white,true),
        text(802,y+12,382,40,evidence,{size:14,color,bold:true,valign:"mid"}),
      ]),
      rect(160, 598, 960, 48, { fill:C.white,line:C.line,width:1,radius:0 }),
      text(182, 610, 916, 24, "Traceability: SRS and design IDs, test markers, workflow artifact and exact source SHA", { size:13,color:C.redDark,bold:true,align:"center" }),
      ...footer("H", true),
    ],
    notes: note(
      "Evidence strength follows the behavior being claimed.",
      "Unit and property tests own value and algorithm invariants. Port contracts own substitution. Integration tests own real database, security and retrieval semantics. Playwright and deployment evidence own visible composition. Stable requirement and design IDs, test markers, workflow artifacts and exact source SHAs keep the path traceable.",
      "Do not use a test count or an old green workflow as a quality claim. Evidence remains bound to its behavior and source revision.",
      "Return to the main conclusion.",
      "As needed",
      "Why retain browser evidence if integration tests pass?",
      "Authentication, navigation, visible provenance and later history are operator-visible behaviors that lower test layers cannot prove in composition.",
      ["docs/assignment/VV/VV.md", "docs/assignment/VV/verification.yaml", "docs/reviewer/screenshot-manifest.md", "Lucide icon library, ISC license"]
    ),
  },
];

function solidFill(hex) { return `<a:solidFill><a:srgbClr val="${rgb(hex)}"/></a:solidFill>`; }
function lineXml(color, width = 1, arrow = false) {
  if (color === "none") return `<a:ln><a:noFill/></a:ln>`;
  const ends = arrow === "both"
    ? '<a:headEnd type="triangle"/><a:tailEnd type="triangle"/>'
    : arrow
      ? '<a:tailEnd type="triangle"/>'
      : "";
  return `<a:ln w="${Math.max(1, Math.round(width * 12700))}">${solidFill(color)}${ends}</a:ln>`;
}
function shapeXfrm(s) {
  const x = s.w < 0 ? s.x + s.w : s.x;
  const y = s.h < 0 ? s.y + s.h : s.y;
  return `<a:xfrm${s.w < 0 ? ' flipH="1"' : ""}${s.h < 0 ? ' flipV="1"' : ""}><a:off x="${e(x)}" y="${e(y)}"/><a:ext cx="${e(Math.abs(s.w))}" cy="${e(Math.abs(s.h))}"/></a:xfrm>`;
}
function paragraphsXml(s, hyperlinkRelId) {
  const lines = String(s.value).split("\n");
  const align = { left: "l", center: "ctr", right: "r" }[s.align || "left"];
  return lines.map((v) => `<a:p><a:pPr algn="${align}"/><a:r><a:rPr lang="en-US" sz="${Math.round((s.size || 18) * 100)}" b="${s.bold ? 1 : 0}"${s.underline ? ' u="sng"' : ""}${s.tracking ? ` spc="${s.tracking}"` : ""}>${solidFill(s.color || C.ink)}<a:latin typeface="Arial"/>${hyperlinkRelId ? `<a:hlinkClick r:id="${hyperlinkRelId}"/>` : ""}</a:rPr><a:t>${esc(v)}</a:t></a:r><a:endParaRPr lang="en-US" sz="${Math.round((s.size || 18) * 100)}"/></a:p>`).join("");
}
function textShapeXml(s, hyperlinkRelId) {
  const id = ++shapeId;
  const fill = s.fill ? solidFill(s.fill) : "<a:noFill/>";
  const margin = e(s.margin ?? 0);
  const anchor = { top: "t", mid: "ctr", bottom: "b" }[s.valign || "top"];
  return `<p:sp><p:nvSpPr><p:cNvPr id="${id}" name="Text ${id}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr>${shapeXfrm(s)}<a:prstGeom prst="rect"><a:avLst/></a:prstGeom>${fill}<a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr wrap="square" anchor="${anchor}" lIns="${margin}" rIns="${margin}" tIns="${margin}" bIns="${margin}"/><a:lstStyle/>${paragraphsXml(s, hyperlinkRelId)}</p:txBody></p:sp>`;
}
function geomShapeXml(s) {
  const id = ++shapeId;
  const prst = s.type === "ellipse" ? "ellipse" : (s.radius ? "roundRect" : "rect");
  const fill = !s.fill || s.fill === "none" ? "<a:noFill/>" : solidFill(s.fill);
  return `<p:sp><p:nvSpPr><p:cNvPr id="${id}" name="Shape ${id}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${shapeXfrm(s)}<a:prstGeom prst="${prst}"><a:avLst/></a:prstGeom>${fill}${lineXml(s.line || "none", s.width || 1)}</p:spPr></p:sp>`;
}
function connectorXml(s) {
  const id = ++shapeId;
  return `<p:cxnSp><p:nvCxnSpPr><p:cNvPr id="${id}" name="Connector ${id}"/><p:cNvCxnSpPr/><p:nvPr/></p:nvCxnSpPr><p:spPr>${shapeXfrm(s)}<a:prstGeom prst="line"><a:avLst/></a:prstGeom>${lineXml(s.color || C.ink, s.width || 1, s.arrow)}</p:spPr></p:cxnSp>`;
}
function pictureXml(s, relId) {
  const id = ++shapeId;
  return `<p:pic><p:nvPicPr><p:cNvPr id="${id}" name="Picture ${id}"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="${relId}"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>${shapeXfrm(s)}<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:ln><a:noFill/></a:ln></p:spPr></p:pic>`;
}

function slideXml(slide, mediaRels, hyperlinkRels) {
  shapeId = 1;
  let imageIndex = 0;
  let hyperlinkIndex = 0;
  const body = slide.shapes.flat().filter(Boolean).map((s) => {
    if (s.type === "text") return textShapeXml(s, s.href ? hyperlinkRels[hyperlinkIndex++].id : undefined);
    if (s.type === "rect" || s.type === "ellipse") return geomShapeXml(s);
    if (s.type === "line") return connectorXml(s);
    if (s.type === "image") return pictureXml(s, mediaRels[imageIndex++].id);
    return "";
  }).join("");
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld>${slide.bg ? `<p:bg><p:bgPr>${solidFill(slide.bg)}<a:effectLst/></p:bgPr></p:bg>` : ""}<p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>${body}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>`;
}

function notesSlideXml(notes) {
  const paras = String(notes).split("\n").map((v) => `<a:p><a:r><a:rPr lang="en-US" sz="1400"><a:latin typeface="Arial"/></a:rPr><a:t>${esc(v)}</a:t></a:r><a:endParaRPr lang="en-US" sz="1400"/></a:p>`).join("");
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr><p:sp><p:nvSpPr><p:cNvPr id="2" name="Notes Placeholder 1"/><p:cNvSpPr txBox="1"/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/>${paras}</p:txBody></p:sp></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>`;
}

async function write(rel, data) {
  const target = path.join(packageDir, rel);
  await fs.mkdir(path.dirname(target), { recursive: true });
  await fs.writeFile(target, data);
}

async function zipDirectory(source, target) {
  const zip = new JSZip();
  async function addDirectory(current) {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const absolute = path.join(current, entry.name);
      if (entry.isDirectory()) {
        await addDirectory(absolute);
      } else if (entry.isFile()) {
        const relative = path.relative(source, absolute).split(path.sep).join("/");
        zip.file(relative, await fs.readFile(absolute));
      }
    }
  }
  await addDirectory(source);
  const data = await zip.generateAsync({
    type: "nodebuffer",
    compression: "DEFLATE",
    compressionOptions: { level: 9 },
  });
  await fs.writeFile(target, data);
}

function baseSlideRels(slideIndex, media, hyperlinks) {
  const rels = [`<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>`];
  let rid = 2;
  for (const m of media) rels.push(`<Relationship Id="rId${rid++}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/${esc(m.name)}"/>`);
  for (const h of hyperlinks) rels.push(`<Relationship Id="rId${rid++}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${esc(h.target)}" TargetMode="External"/>`);
  rels.push(`<Relationship Id="rId${rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide${slideIndex}.xml"/>`);
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${rels.join("")}</Relationships>`;
}

function wrapPreviewText(value, width, fontSize) {
  const maxChars = Math.max(5, Math.floor(width / (fontSize * 0.52)));
  const result = [];
  for (const raw of String(value).split("\n")) {
    if (!raw) { result.push(""); continue; }
    const words = raw.split(/\s+/);
    let current = "";
    for (const word of words) {
      const candidate = current ? `${current} ${word}` : word;
      if (candidate.length > maxChars && current) {
        result.push(current);
        current = word;
      } else current = candidate;
    }
    if (current) result.push(current);
  }
  return result;
}
function textSvg(s) {
  const renderSize = (s.size || 18) * 1.333;
  const pad = s.margin ?? 0;
  const lines = wrapPreviewText(s.value, Math.max(10, s.w - 2 * pad), renderSize);
  const anchor = s.align === "center" ? "middle" : s.align === "right" ? "end" : "start";
  const x = s.align === "center" ? s.x + s.w / 2 : s.align === "right" ? s.x + s.w - pad : s.x + pad;
  const lineHeight = renderSize * 1.17;
  const totalHeight = lines.length * lineHeight;
  const baseY = s.valign === "mid" ? s.y + Math.max(renderSize, (s.h - totalHeight) / 2 + renderSize) : s.valign === "bottom" ? s.y + s.h - totalHeight + renderSize : s.y + pad + renderSize;
  const bg = s.fill ? `<rect x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" fill="#${rgb(s.fill)}"/>` : "";
  const tspans = lines.map((v, i) => `<tspan x="${x}" dy="${i === 0 ? 0 : lineHeight}">${esc(v)}</tspan>`).join("");
  return `${bg}<text x="${x}" y="${baseY}" text-anchor="${anchor}" font-family="Arial" font-size="${renderSize}" font-weight="${s.bold ? 700 : 400}" fill="#${rgb(s.color || C.ink)}">${tspans}</text>`;
}
function previewShapeSvg(s) {
  if (s.type === "text") return textSvg(s);
  if (s.type === "rect") return `<rect x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" rx="${s.radius || 0}" fill="${!s.fill || s.fill === "none" ? "none" : `#${rgb(s.fill)}`}" stroke="${!s.line || s.line === "none" ? "none" : `#${rgb(s.line)}`}" stroke-width="${s.width || 1}"/>`;
  if (s.type === "ellipse") return `<ellipse cx="${s.x + s.w / 2}" cy="${s.y + s.h / 2}" rx="${s.w / 2}" ry="${s.h / 2}" fill="${!s.fill || s.fill === "none" ? "none" : `#${rgb(s.fill)}`}" stroke="${!s.line || s.line === "none" ? "none" : `#${rgb(s.line)}`}" stroke-width="${s.width || 1}"/>`;
  if (s.type === "line") return `<line x1="${s.x}" y1="${s.y}" x2="${s.x + s.w}" y2="${s.y + s.h}" stroke="#${rgb(s.color || C.ink)}" stroke-width="${s.width || 1}" marker-start="${s.arrow === "both" ? "url(#arrow-start)" : ""}" marker-end="${s.arrow ? "url(#arrow)" : ""}"/>`;
  if (s.type === "image") return `<image x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" href="${s.previewHref}" preserveAspectRatio="xMidYMid meet"/>`;
  return "";
}

async function prepareImageShapes() {
  const cached = new Map();
  for (const shape of slides.flatMap((slide) => slide.shapes.flat()).filter((s) => s?.type === "image")) {
    let asset = cached.get(shape.src);
    if (!asset) {
      const buffer = await fs.readFile(shape.src);
      const metadata = await sharp(buffer).metadata();
      const mime = metadata.format === "svg" ? "image/svg+xml" : `image/${metadata.format}`;
      asset = {
        width: metadata.width,
        height: metadata.height,
        dataUri: `data:${mime};base64,${buffer.toString("base64")}`,
      };
      cached.set(shape.src, asset);
    }
    shape.previewHref = asset.dataUri;
    if (shape.fit === "contain" && asset.width && asset.height) {
      const scale = Math.min(shape.w / asset.width, shape.h / asset.height);
      const width = asset.width * scale;
      const height = asset.height * scale;
      shape.x += (shape.w - width) / 2;
      shape.y += (shape.h - height) / 2;
      shape.w = width;
      shape.h = height;
    }
  }
}

function lucideNodeXml([tag, attrs]) {
  const serialized = Object.entries(attrs)
    .filter(([key]) => key !== "key")
    .map(([key, value]) => `${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}="${esc(value)}"`)
    .join(" ");
  return `<${tag} ${serialized}/>`;
}

async function renderLucideAssets() {
  await fs.mkdir(iconDir, { recursive: true });
  const specs = new Map();
  for (const shape of slides.flatMap((slide) => slide.shapes.flat()).filter(Boolean)) {
    if (shape.iconName) specs.set(shape.src, shape);
  }
  for (const spec of specs.values()) {
    try {
      await fs.access(spec.src);
      continue;
    } catch {
      // Render the missing variant below when Lucide is available.
    }
    if (!lucide) throw new Error(`Missing retained Lucide icon and runtime package: ${spec.iconName}`);
    const nodes = lucide[spec.iconName];
    if (!nodes) throw new Error(`Unknown Lucide icon: ${spec.iconName}`);
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="192" height="192" fill="none" stroke="#${rgb(spec.iconColor)}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${nodes.map(lucideNodeXml).join("")}</svg>`;
    await sharp(Buffer.from(svg)).resize(192, 192).png().toFile(spec.src);
  }
}

async function build() {
  await fs.mkdir(packageDir, { recursive: true });
  await fs.mkdir(path.join(packageDir, "ppt", "media"), { recursive: true });
  await fs.mkdir(previewDir, { recursive: true });
  await fs.mkdir(outputDir, { recursive: true });
  await renderLucideAssets();
  await prepareImageShapes();

  const overrides = [
    '<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>',
    '<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>',
    '<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>',
    '<Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>',
    '<Override PartName="/ppt/notesMasters/notesMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml"/>',
    '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>',
    '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>',
    '<Override PartName="/ppt/presProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"/>',
    '<Override PartName="/ppt/viewProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"/>',
    '<Override PartName="/ppt/tableStyles.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml"/>',
  ];
  for (let i = 1; i <= slides.length; i++) {
    overrides.push(`<Override PartName="/ppt/slides/slide${i}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>`);
    overrides.push(`<Override PartName="/ppt/notesSlides/notesSlide${i}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"/>`);
  }
  await write("[Content_Types].xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="svg" ContentType="image/svg+xml"/><Default Extension="png" ContentType="image/png"/>${overrides.join("")}</Types>`);
  await write("_rels/.rels", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>`);
  await write("docProps/core.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>Customer Activity Analytics</dc:title><dc:subject>Swissquote pilot presentation</dc:subject><dc:creator>Nicolas Cazin</dc:creator><cp:keywords>SpecGraph; reviewable AI engineering; GitHub control plane</cp:keywords><dc:description>Working presentation deck with speaker guardrails and technical appendix.</dc:description><cp:lastModifiedBy>Nicolas Cazin</cp:lastModifiedBy><dcterms:created xsi:type="dcterms:W3CDTF">2026-09-04T08:00:00Z</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">2026-09-04T08:00:00Z</dcterms:modified></cp:coreProperties>`);
  await write("docProps/app.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"><Application>Microsoft Office PowerPoint</Application><PresentationFormat>Widescreen</PresentationFormat><Slides>${slides.length}</Slides><Notes>${slides.length}</Notes><Company></Company><AppVersion>16.0000</AppVersion></Properties>`);

  const slideIds = slides.map((_, i) => `<p:sldId id="${256 + i}" r:id="rId${i + 2}"/>`).join("");
  await write("ppt/presentation.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" saveSubsetFonts="1"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:notesMasterIdLst><p:notesMasterId r:id="rId${slides.length + 2}"/></p:notesMasterIdLst><p:sldIdLst>${slideIds}</p:sldIdLst><p:sldSz cx="${SLIDE_CX}" cy="${SLIDE_CY}" type="screen16x9"/><p:notesSz cx="6858000" cy="9144000"/><p:defaultTextStyle><a:defPPr><a:defRPr lang="en-US"/></a:defPPr></p:defaultTextStyle></p:presentation>`);
  const presRels = [`<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>`];
  slides.forEach((_, i) => presRels.push(`<Relationship Id="rId${i + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${i + 1}.xml"/>`));
  presRels.push(`<Relationship Id="rId${slides.length + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster" Target="notesMasters/notesMaster1.xml"/>`);
  await write("ppt/_rels/presentation.xml.rels", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${presRels.join("")}<Relationship Id="rId${slides.length + 3}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps" Target="presProps.xml"/><Relationship Id="rId${slides.length + 4}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps" Target="viewProps.xml"/><Relationship Id="rId${slides.length + 5}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles" Target="tableStyles.xml"/></Relationships>`);
  await write("ppt/presProps.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:presentationPr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:showPr useTimings="0" showNarration="0"/></p:presentationPr>`);
  await write("ppt/viewProps.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:viewPr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" lastView="sldView"><p:normalViewPr><p:restoredLeft sz="15620"/><p:restoredTop sz="94660"/></p:normalViewPr><p:slideViewPr><p:cSldViewPr><p:cViewPr varScale="1"><p:scale><a:sx n="100" d="100"/><a:sy n="100" d="100"/></p:scale><p:origin x="0" y="0"/></p:cViewPr><p:guideLst/></p:cSldViewPr></p:slideViewPr><p:notesTextViewPr><p:cViewPr><p:scale><a:sx n="100" d="100"/><a:sy n="100" d="100"/></p:scale><p:origin x="0" y="0"/></p:cViewPr></p:notesTextViewPr><p:gridSpacing cx="78028800" cy="78028800"/></p:viewPr>`);
  await write("ppt/tableStyles.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><a:tblStyleLst xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" def="{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"/>`);
  await write("ppt/theme/theme1.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="SpecGraph"><a:themeElements><a:clrScheme name="SpecGraph"><a:dk1><a:srgbClr val="${C.navy}"/></a:dk1><a:lt1><a:srgbClr val="${C.white}"/></a:lt1><a:dk2><a:srgbClr val="${C.ink}"/></a:dk2><a:lt2><a:srgbClr val="${C.pale}"/></a:lt2><a:accent1><a:srgbClr val="${C.red}"/></a:accent1><a:accent2><a:srgbClr val="${C.blue}"/></a:accent2><a:accent3><a:srgbClr val="${C.green}"/></a:accent3><a:accent4><a:srgbClr val="${C.amber}"/></a:accent4><a:accent5><a:srgbClr val="667984"/></a:accent5><a:accent6><a:srgbClr val="977176"/></a:accent6><a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme><a:fontScheme name="Arial"><a:majorFont><a:latin typeface="Arial"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Arial"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont></a:fontScheme><a:fmtScheme name="SpecGraph"><a:fillStyleLst>${solidFill(C.red)}${solidFill(C.ink)}${solidFill(C.pale)}</a:fillStyleLst><a:lnStyleLst>${lineXml(C.ink,1)}${lineXml(C.red,2)}${lineXml(C.line,1)}</a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst>${solidFill(C.white)}${solidFill(C.pale)}${solidFill(C.navy)}</a:bgFillStyleLst></a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>`);
  const emptyTree = `<p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree>`;
  await write("ppt/slideMasters/slideMaster1.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld name="SpecGraph Master">${emptyTree}</p:cSld><p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" folHlink="folHlink" hlink="hlink" tx1="dk1" tx2="dk2"/><p:sldLayoutIdLst><p:sldLayoutId id="1" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle><a:lvl1pPr algn="l"><a:defRPr sz="3200" b="1"/></a:lvl1pPr></p:titleStyle><p:bodyStyle><a:lvl1pPr><a:defRPr sz="1800"/></a:lvl1pPr></p:bodyStyle><p:otherStyle><a:defPPr><a:defRPr lang="en-US"/></a:defPPr></p:otherStyle></p:txStyles></p:sldMaster>`);
  await write("ppt/slideMasters/_rels/slideMaster1.xml.rels", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>`);
  await write("ppt/slideLayouts/slideLayout1.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1"><p:cSld name="Blank">${emptyTree}</p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>`);
  await write("ppt/slideLayouts/_rels/slideLayout1.xml.rels", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>`);
  await write("ppt/notesMasters/notesMaster1.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notesMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld name="Notes Master">${emptyTree}</p:cSld><p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" folHlink="folHlink" hlink="hlink" tx1="dk1" tx2="dk2"/><p:hf hdr="0" ftr="0" dt="1" sldNum="1"/><p:notesStyle><a:lvl1pPr marL="0" algn="l" defTabSz="457200" rtl="0" eaLnBrk="1" latinLnBrk="0" hangingPunct="1"><a:defRPr sz="1400" kern="1200"><a:latin typeface="Arial"/></a:defRPr></a:lvl1pPr></p:notesStyle></p:notesMaster>`);
  await write("ppt/notesMasters/_rels/notesMaster1.xml.rels", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>`);

  for (let i = 0; i < slides.length; i++) {
    const slide = slides[i];
    const media = slide.shapes.flat().filter((s) => s?.type === "image").map((s, idx) => ({ ...s, id: `rId${2 + idx}`, name: `slide-${i + 1}-${idx + 1}${path.extname(s.src)}` }));
    const hyperlinks = slide.shapes.flat().filter((s) => s?.type === "text" && s.href).map((s, idx) => ({ id: `rId${2 + media.length + idx}`, target: s.href }));
    for (const m of media) await fs.copyFile(m.src, path.join(packageDir, "ppt", "media", m.name));
    await write(`ppt/slides/slide${i + 1}.xml`, slideXml(slide, media, hyperlinks));
    await write(`ppt/slides/_rels/slide${i + 1}.xml.rels`, baseSlideRels(i + 1, media, hyperlinks));
    await write(`ppt/notesSlides/notesSlide${i + 1}.xml`, notesSlideXml(slide.notes));
    await write(`ppt/notesSlides/_rels/notesSlide${i + 1}.xml.rels`, `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster" Target="../notesMasters/notesMaster1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="../slides/slide${i + 1}.xml"/></Relationships>`);

    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs><marker id="arrow" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#${C.red}"/></marker><marker id="arrow-start" markerWidth="10" markerHeight="7" refX="1" refY="3.5" orient="auto"><polygon points="10 0, 0 3.5, 10 7" fill="#${C.red}"/></marker></defs><rect width="${W}" height="${H}" fill="#${slide.bg || C.white}"/>${slide.shapes.flat().filter(Boolean).map(previewShapeSvg).join("")}</svg>`;
    await sharp(Buffer.from(svg)).png().toFile(path.join(previewDir, `slide-${String(i + 1).padStart(2, "0")}.png`));
  }

  const thumbs = [];
  for (let i = 0; i < slides.length; i++) {
    const data = await sharp(path.join(previewDir, `slide-${String(i + 1).padStart(2, "0")}.png`)).resize(320, 180).png().toBuffer();
    thumbs.push({ input: data, left: (i % 4) * 334, top: Math.floor(i / 4) * 194 });
  }
  await sharp({ create: { width: 4 * 334, height: Math.ceil(slides.length / 4) * 194, channels: 4, background: "#D8DDE2" } }).composite(thumbs).png().toFile(path.join(previewDir, "contact-sheet.png"));

  await fs.rm(zipPath, { force: true });
  await fs.rm(draftPath, { force: true });
  await fs.rm(candidatePath, { force: true });
  await fs.rm(validationReceiptPath, { force: true });
  await fs.rm(pptxPath, { force: true });
  await zipDirectory(packageDir, zipPath);
  await fs.rename(zipPath, draftPath);

  const skillDir = process.env.PRESENTATIONS_SKILL_DIR;
  const runtimePython = process.env.RUNTIME_PYTHON;
  if (process.env.RUNTIME_NODE_MODULES && skillDir && runtimePython) {
    const { FileBlob, PresentationFile } = runtimeRequire("@oai/artifact-tool");
    const imported = await PresentationFile.importPptx(await FileBlob.load(draftPath));
    const exported = await PresentationFile.exportPptx(imported);
    await exported.save(candidatePath);
    const { finalizePresentation } = await import(pathToFileURL(path.join(skillDir, "container_tools", "artifact_tool_utils.mjs")).href);
    await finalizePresentation({
      workspaceDir: workspace,
      candidatePath,
      finalPath: pptxPath,
      explicitTotalSlideCount: slides.length,
      requiredNativeTableOwnerSlides: [],
      requiredNativeChartOwnerSlides: [],
      pythonExecutable: runtimePython,
      integrityValidatorPath: path.join(skillDir, "container_tools", "inspect_presentation_package_integrity.py"),
      layoutValidatorPath: path.join(skillDir, "container_tools", "inspect_presentation_layout_geometry.py"),
      layoutArgs: ["--expected-slide-size-emu", `${SLIDE_CX},${SLIDE_CY}`, "--validate-bullet-geometry", "--validate-heading-fit"],
      fontPolicy: { basis: "design", families: ["Arial"] },
      verifyArtifactToolImport: true,
      receiptPath: validationReceiptPath,
    });
  } else {
    await fs.copyFile(draftPath, pptxPath);
  }
  console.log(JSON.stringify({ pptxPath, slides: slides.length, previewDir }, null, 2));
}

await build();
