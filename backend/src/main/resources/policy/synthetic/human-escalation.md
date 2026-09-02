# Synthetic human escalation guidance

This demonstration policy is synthetic and exists only to exercise grounded retrieval. It is not institutional policy.

Automated analysis does not make the final escalation decision. The operator should review source transaction facts and persisted source-risk evidence, then consider separately identified detector signals and retrieved policy context. Generated model findings may summarize those inputs but cannot rewrite source evidence or manufacture a risk assessment.

When grounding is missing, irrelevant or unavailable, the system should fail without presenting a completed grounded analysis. When evidence is available, retained history should identify the operator, retrieved policy chunks, detector provenance and model/backend provenance so the decision-support chain remains reviewable. Human escalation remains an explicit operator action rather than an automatic consequence of a model score.
