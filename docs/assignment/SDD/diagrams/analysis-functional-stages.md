# R4 functional analysis stages

The architecture is named by **function**, not by vendor. See `analysis-functional-stages.puml` for the controlled source diagram.

1. **Primitive signal analysis** — interchangeable deterministic/rule, Bayesian/sequential, fuzzy-logic, Random Forest/Extra Trees, anomaly or graph detectors behind `RiskSignalDetectorPort`, producing canonical `RiskSignalEvidence` only.
2. **Evidence grounding and retrieval** — receives the complete `CustomerSnapshot` behind `PolicyKnowledgePort` and returns ranked `PolicyEvidence` without changing source or detector authority.
3. **Final advisory synthesis** — interchangeable deterministic/local or live-LLM implementations behind `AnalysisModelPort`, producing the structured `AnalysisResult`.

Immediately before Stage 3, application-owned `AnalysisContextBuilder` separates exact complete-input totals from deterministically selected activity, source-risk, detector and policy details. Every selected source-risk fact retains its backing selected activity. The resulting bounded `AnalysisEvidenceEnvelope` is the only input to `AnalysisModelPort`; Stage 1 and Stage 2 remain complete-snapshot consumers. This model-context sizing is independent from operator-review pagination and provider token ceilings.

OpenAI is therefore an implementation of Stage 3, while Bayesian, fuzzy and Random Forest are implementations of Stage 1. pgvector/local embeddings are implementation details of Stage 2 retrieval.
