# Customer Activity Analytics — Software Requirements Specification

**Document ID:** CAA-SRS-001  
**Baseline revision:** 1  
**Language:** English  
**Normative companion index:** [`requirements.yaml`](requirements.yaml)

## 1. Purpose and authority

This SRS is the first controlled normative baseline for the Customer Activity Analytics reference application. It translates the supplied take-home statement and database schema into stable, reviewable requirements without deriving requirements from an implementation.

The authority order is:

1. `SRC-001`: the supplied Customer Activity Analytics take-home statement and attached database schema, retained outside the durable repository;
2. this SRS for accepted application requirements, constraints, invariants, assumptions and acceptance criteria;
3. `requirements.yaml` for machine-readable identity, provenance and traceability metadata;
4. Inception and later design/test artefacts as derived views and implementation evidence.

If a generated view disagrees with this SRS, this SRS wins. If this SRS disagrees with a newly verified source fact, the source fact triggers a controlled SRS revision rather than a silent reinterpretation.

## 2. Scope

The product is a web dashboard used by customer-care operators to inspect customer activity and risk, request AI-assisted analysis, retrieve relevant unstructured policy knowledge, and review previously generated analyses.

The supplied relational schema covers three activity families:

- card activity;
- payment activity;
- cryptocurrency activity.

The schema also provides transaction-level risk signals and the rules that produced them.

### 2.1 Functional branch

The functional branch specifies observable operator capabilities and domain behaviour. It intentionally does not prescribe controllers, frameworks, database adapters, vector stores, LLM vendors or deployment products.

### 2.2 Technical and quality branch

The technical branch records only constraints needed to make the supplied assignment reviewable, reproducible and safe to exercise. Architecture and technology decisions belong in the SDD/ADR layer unless the source itself makes them normative.

This separation follows a 2TUP-style distinction between functional needs and technical/quality constraints; later design work converges the two branches.

## 3. Source facts and non-requirement preferences

`SRC-001` explicitly requires or supplies the following facts:

- operators search a customer by Customer ID;
- customer activity is stored in a relational database;
- activity has CARD, PAYMENT and CRYPTO forms;
- the application provides a clear customer-activity overview;
- an operator can request AI-powered analysis;
- AI analysis provides a risk level, findings summary and recommendations;
- different operators can log in;
- RAG retrieves relevant unstructured knowledge and policies;
- AI analysis results are persisted for later review;
- the supplied schema defines transaction, activity-specialization, risk-assessment and risk-rule data;
- actual LLM calls may be stubbed;
- delivery is a Git repository with run/architecture/decision/assumption documentation and a 10–15 minute demo;
- delivery also includes summaries of LLM choices and agent instructions.

The source lists Java 17+, Spring Boot, Hibernate/JPA, React and a relational database as **preferred** technologies. Preference is recorded as source context, not converted into a functional requirement. Concrete stack selection belongs to design.

## 4. Functional requirements

### FR-CUST-001 — Search customer by Customer ID

**Priority:** MANDATORY  
**Origin:** `SRC-001`

The application shall allow an operator to search for a customer using a Customer ID.

Acceptance: `AC-CUST-001`, `AC-CUST-002`.

### FR-ACT-001 — Review customer activity

**Priority:** MANDATORY  
**Origin:** `SRC-001`

For a selected customer, the application shall present a clear overview of the customer's persisted activity.

Acceptance: `AC-ACT-001`.

### FR-ACT-002 — Distinguish the three supplied activity families

**Priority:** MANDATORY  
**Origin:** `SRC-001`

The activity overview shall distinguish CARD, PAYMENT and CRYPTO activity and expose the applicable type-specific information supplied by the relational schema.

Acceptance: `AC-ACT-002`.

### FR-RISK-001 — Review customer risk evidence

**Priority:** MANDATORY  
**Origin:** `SRC-001` goal plus supplied risk schema

For a selected customer, the application shall expose the available risk evidence associated with the customer's transactions, including the triggered risk rule and score contribution represented by the supplied schema.

Acceptance: `AC-RISK-001`.

### FR-AI-001 — Request AI analysis

**Priority:** MANDATORY  
**Origin:** `SRC-001`

The operator shall be able to request an AI-assisted analysis of the selected customer's activity.

Acceptance: `AC-AI-001`.

### FR-AI-002 — Produce structured analysis output

**Priority:** MANDATORY  
**Origin:** `SRC-001`

Each completed AI analysis shall contain, as distinct structured elements:

1. a risk level;
2. a findings summary;
3. recommendations.

Acceptance: `AC-AI-002`.

### FR-AUTH-001 — Authenticate distinct operators

**Priority:** MUST_HAVE  
**Origin:** `SRC-001`

The application shall support login by more than one distinct customer-care operator identity.

Acceptance: `AC-AUTH-001`.

### FR-RAG-001 — Retrieve relevant policy knowledge

**Priority:** MUST_HAVE  
**Origin:** `SRC-001`

When preparing AI analysis, the application shall retrieve relevant unstructured knowledge or policy content through a RAG capability and make the retrieved evidence available to the analysis context.

Acceptance: `AC-RAG-001`.

### FR-HIST-001 — Persist completed AI analyses

**Priority:** MUST_HAVE  
**Origin:** `SRC-001`

The application shall persist completed AI analysis results so they survive the request that produced them.

Acceptance: `AC-HIST-001`.

### FR-HIST-002 — Review previous AI analyses

**Priority:** MUST_HAVE  
**Origin:** `SRC-001`

For a selected customer, an operator shall be able to review previously persisted AI analyses.

Acceptance: `AC-HIST-002`.

## 5. Domain and data invariants

### INV-DATA-001 — Activity belongs to a customer through its transaction

Every displayed activity record shall be attributable to exactly one `transactions.transaction_id`, and that base transaction carries the `customer_id` used to select the customer.

**Origin:** supplied relational keys plus derived consistency rule.

### INV-DATA-002 — Activity specialization matches activity type

A CARD, PAYMENT or CRYPTO detail record shall only be interpreted as the specialization corresponding to the base transaction's `activity_type`.

**Origin:** supplied schema plus derived consistency rule.

### INV-RISK-001 — Risk evidence remains source-derived

Risk signals shown as existing transaction evidence shall originate from supplied/persisted `risk_assessments` and `risk_rules` data. AI-generated prose shall not be represented as an existing risk-assessment record.

**Origin:** derived correctness boundary from the supplied risk schema.

### INV-AI-001 — Analysis output contract is provider-neutral

The risk level, findings summary and recommendations contract shall not change depending on whether the analysis adapter is deterministic, stubbed or backed by an external LLM.

**Origin:** `SRC-001` permits stubs for actual LLM calls; derived interoperability constraint.

### INV-HIST-001 — Persisted analyses remain attributable

A persisted analysis shall retain enough identity to determine the customer, generating operator, generation time and structured analysis result.

**Origin:** derived from multi-operator login plus later-review requirements.

## 6. Technical and quality constraints

### NFR-REP-001 — Reviewer-reproducible execution

**Priority:** MANDATORY  
**Origin:** derived from repository delivery and run-documentation requirement.

A reviewer following the documented local run procedure shall be able to start the baseline application without requiring an external LLM account.

Acceptance: `AC-REP-001`.

### NFR-VER-001 — Deterministic baseline verification

**Priority:** MANDATORY  
**Origin:** project verification constraint; compatible with `SRC-001` stub allowance.

Mandatory baseline behaviour shall have an executable deterministic verification path that does not depend on nondeterministic output from an external LLM.

Acceptance: `AC-VER-001`.

### NFR-SEC-001 — Protected operator capabilities require authentication

**Priority:** MUST_HAVE  
**Origin:** derived from `FR-AUTH-001`.

Customer activity, risk, analysis and history capabilities shall not be available to an unauthenticated operator session.

Acceptance: `AC-SEC-001`.

### CON-AI-001 — External provider use is optional

No mandatory acceptance criterion shall require a live external LLM provider. A live provider adapter may be demonstrated additionally, but the assignment remains exercisable through the provider-neutral analysis contract.

**Origin:** `SRC-001` explicitly permits stubs for actual LLM calls.

### CON-DATA-001 — Default demo data is synthetic

The repository shall not require real customer data to demonstrate the assignment. Seeded/default demonstration data shall be synthetic.

**Origin:** project confidentiality safeguard; the source supplies schema, not production customer records.

### CON-AI-002 — External transmission is opt-in

The default configuration shall not transmit customer/activity/policy content to an external AI provider. External transmission requires explicit adapter configuration and shall only be exercised with data permitted for that provider.

**Origin:** project confidentiality safeguard resolving `AMB-CNF-001` conservatively.

## 7. Source-schema contract

The supplied schema is treated as a domain input contract, not as an instruction to invent a generic risk engine.

### 7.1 Base transaction

`transactions` supplies:

- `transaction_id` UUID primary key;
- `customer_id` UUID foreign key to an unspecified `customers` relation;
- `activity_type` in CARD / PAYMENT / CRYPTO;
- amount, currency, status and creation timestamp.

### 7.2 Activity specialization

Each type-specific table is keyed by `transaction_id` and links to the base transaction:

- `card_activity` carries masked PAN, card type, merchant/MCC, card-present flag, authorization code and optional decline reason;
- `payment_activity` carries payment method, sender/receiver account and receiver-bank country;
- `crypto_activity` carries blockchain, source/destination wallets, transaction hash and optional exchange name.

### 7.3 Risk layer

`risk_assessments` supports multiple risk signals per transaction over time and references `risk_rules`. A signal records when a rule fired and its score contribution. A risk rule supplies a name, applicable activity family, threshold logic and default weight.

The source does not require the application to execute arbitrary `threshold_logic` at runtime. Reading and presenting supplied/persisted risk evidence is sufficient unless a later accepted requirement says otherwise.

## 8. Ambiguities preserved by the baseline

### AMB-DATA-001 — Customer representation

The schema references `customers` but does not define that table or customer attributes.

### AMB-AUTH-001 — Authentication and authorization model

The source requires login by different operators but defines no operator schema, identity provider, roles or authorization hierarchy.

### AMB-HIST-001 — AI-analysis persistence model

Persistence is required, but the source supplies no table/schema for generated analyses or provenance.

### AMB-RISK-001 — Risk-level scale and aggregation

The source requires an AI-produced risk level but does not define the allowed scale. The risk schema exposes score contributions but no customer-level aggregation formula or thresholds.

### AMB-RAG-001 — Knowledge corpus and retrieval relevance

RAG is required, but the policy corpus, chunking, ranking, minimum evidence and provenance format are unspecified.

### AMB-MON-001 — Meaning of "monitor"

The goal uses "monitor" without specifying polling, streaming, alerts, refresh cadence or merely inspecting current information.

### AMB-LLM-001 — LLM provider and model

No provider or model is mandated; actual LLM calls may be stubbed.

### AMB-DEP-001 — Deployment target

No external hosting platform, availability target or production SLO is specified.

### AMB-CNF-001 — External-model data policy

The source does not state whether customer/activity/policy information may be transmitted to an external LLM provider.

## 9. Explicit baseline assumptions

Assumptions resolve only what is necessary to build and review the reference application. They do not masquerade as source facts.

### ASM-DATA-001 — Minimal customer records

A minimal project-owned customer representation may be added so the supplied `customer_id` foreign key can resolve and Customer-ID search can be demonstrated. No customer attributes beyond those needed for the demo are assumed.

### ASM-AUTH-001 — Multiple local demo operators

At least two distinct demo operator identities are sufficient to satisfy the baseline login requirement. Role differentiation is not assumed.

### ASM-HIST-001 — Minimal analysis provenance

The persistence model may add project-owned fields for analysis identity, customer identity, operator identity, generation time, structured output and evidence/provider provenance needed to satisfy `INV-HIST-001`.

### ASM-RISK-001 — Demonstration risk-level vocabulary

Until the source defines a scale, the application-level structured analysis contract uses `LOW`, `MEDIUM`, and `HIGH`. This vocabulary is a reversible project assumption, not a supplied banking policy.

### ASM-RAG-001 — Synthetic demonstration policy corpus

A small synthetic policy/knowledge corpus is sufficient to demonstrate retrieval and grounding. It shall not be presented as real institutional policy.

### ASM-MON-001 — Monitoring means inspect plus refresh

For the baseline, "monitor" means inspect current persisted activity/risk information and refresh it. Streaming ingestion and alerting are outside the accepted baseline.

### ASM-DEP-001 — Local execution is the mandatory deployment baseline

A documented local deployment is sufficient for assignment acceptance. A remote demo deployment is useful delivery evidence but not a source requirement.

## 10. Acceptance criteria

### AC-CUST-001

Given a seeded existing Customer ID, a logged-in operator can select that customer and reach the customer's dashboard.

### AC-CUST-002

Given an unknown Customer ID, the application reports that the customer is not found and does not fabricate a customer record.

### AC-ACT-001

For a seeded customer with transactions, the dashboard presents persisted activity with at least transaction identity, type, amount/currency, status and occurrence time.

### AC-ACT-002

For seeded CARD, PAYMENT and CRYPTO transactions, the dashboard identifies the correct family and exposes the applicable family-specific details from the supplied schema.

### AC-RISK-001

For a seeded transaction with risk assessments, the dashboard exposes the triggered rule identity/name, trigger time and score contribution and associates that evidence with the correct transaction/customer.

### AC-AI-001

From a selected customer's dashboard, an operator can initiate analysis and receive a completed result through the same application flow.

### AC-AI-002

A completed result is machine-validated as containing one risk level from the baseline vocabulary, a non-empty findings summary and one or more recommendations.

### AC-AUTH-001

Two distinct seeded operator identities can each authenticate successfully, and an invalid credential set does not authenticate.

### AC-RAG-001

For a seeded policy corpus with a policy relevant to the selected analysis scenario, the analysis context contains at least one retrieved relevant policy fragment with enough source identity to review what was used.

### AC-HIST-001

After an analysis completes, restarting or reloading the request flow does not erase the persisted analysis from the configured persistent store.

### AC-HIST-002

For a customer with prior analyses, an authenticated operator can list and inspect at least the generation time, generating operator, risk level, findings and recommendations of a previous result.

### AC-REP-001

Following the repository's documented local procedure from a clean checkout starts the required application dependencies and permits the mandatory read-and-analysis demonstration using the deterministic adapter.

### AC-VER-001

The automated baseline test suite can exercise mandatory requirements without network access to an external LLM provider and returns a process-level pass/fail result.

### AC-SEC-001

An unauthenticated request to a protected customer/activity/risk/analysis/history capability is rejected or redirected to authentication; successful login enables the capability.

## 11. Delivery requirements

### DEL-001 — Git repository and README

The delivery shall be a Git repository with a README explaining how to run the application, its architecture, main design decisions and assumptions.

### DEL-002 — Short demo

The delivery shall support a 10–15 minute demonstration of the implemented result.

### DEL-003 — LLM-choice summary

The delivery shall include a summary of the selected LLM approach/provider choices, including use of stubs or deterministic adapters where applicable.

### DEL-004 — Agent-instruction summary

The delivery shall include a short summary of the instructions given to AI development agents.

## 12. Explicitly excluded from this baseline

The following are not accepted requirements unless a later source clarification or separately reviewed project decision promotes them:

- generic runtime evaluation of arbitrary risk-rule expressions;
- microservices, event streaming or Kafka;
- Kubernetes;
- a separate vector database;
- a specific external LLM provider;
- live external-LLM access as a prerequisite for tests or demo;
- real-time alerting or streaming monitoring;
- role-based authorization beyond authenticated operator access;
- production banking SLOs or production security certification;
- filtering, pagination or enriched transaction-detail UX beyond what is needed to present the supplied schema clearly.

## 13. Requirement identity and revision semantics

- Normative IDs are stable and shall never be recycled for a different meaning.
- Editorial clarification that does not alter observable meaning keeps the same ID and increments the document baseline revision.
- A semantic replacement receives a new ID and records an explicit `supersedes` relation in the machine-readable index.
- Removed or replaced IDs remain discoverable in history; they are not silently deleted and renumbered.
- These semantics describe requirement history only. They do not duplicate GitHub issue or pull-request lifecycle.

## 14. Next controlled derivations

This baseline is deliberately implementation-agnostic. The next specification steps are:

1. map accepted requirements and invariants to design elements and ADRs;
2. map acceptance criteria to deterministic V&V assets;
3. implement the first R0/R1 vertical slice without changing requirements merely to fit the code.
