# R4 functional analysis stages

The architecture is named by **function**, not by vendor. See `analysis-functional-stages.puml` for the controlled source diagram.

1. **Primitive signal analysis** — interchangeable deterministic/rule, Bayesian/sequential, fuzzy-logic, Random Forest/Extra Trees, anomaly or graph detectors behind `RiskSignalDetectorPort`, producing canonical `RiskSignalEvidence` only.
2. **Evidence grounding and fusion** — combines source facts, detector artifacts and retrieved policy evidence into the canonical `AnalysisEvidenceEnvelope` without collapsing their semantic authority.
3. **Final advisory synthesis** — interchangeable deterministic/local or live-LLM implementations behind `AnalysisModelPort`, producing the structured `AnalysisResult`.

OpenAI is therefore an implementation of Stage 3, while Bayesian, fuzzy and Random Forest are implementations of Stage 1. pgvector/local embeddings are implementation details of Stage 2 retrieval.
