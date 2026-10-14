# Project readiness — 2026-10-14

This is a preparation assessment based on local source and existing output inspection. It is not an admissions ranking or a full code/security review.

| Project | Reason to develop further | Work before public presentation |
|---|---|---|
| StudentBay (Java) | Full transaction workflows, database layer, tests, design and contribution documents | Preserve group provenance; verify claimed contributions; run tests; write a concise English README and demo with synthetic accounts; review auth, external integrations, and uploaded images. |
| MovieSystem / local eduaccess | Existing public project with domain logic and multiple test classes | Verify tests actually discovered by Maven, including `*IT` classes; show a demo and personal contribution; retain current repository history. This portfolio snapshot does not update the existing repository. |
| Selenium FinalProjects | Page Object structure with login, cart, product, and checkout tests | Add a reproducible browser/test setup; review target website and demo-only checkout; audit dependencies and generated artifacts. No external browser tests were executed here. |
| Delivery optimization | Graph modeling plus small-instance exact search | State the implemented objective carefully: Activity B compares completion time and then total time after the one-truck check; document search constraints and runtime growth. README discussion of a greedy comparison does not by itself establish that an executable benchmark exists. Keep input redistribution review open. |
| NeuMini | C implementation of a disk image, CLI, and host process handling | Use the nested `neumini/` build directory; the outer Makefile refers to a nonexistent outer `src/`. Document platform assumptions and each group member's contribution. |
| Appointment scheduling | Connects synthetic generation, classification, threshold choices, and operational simulation | Record-level CSVs and model binary omitted pending provenance review. Add a dependency file and synthetic fixture generator. Reconcile result summaries and dashboard image names. Existing metrics show limited discrimination and low recall at the reported default threshold. Evaluate class imbalance and separate tuning from held-out evaluation. |
| Library system | Smaller complete web workflow | Local SQLite database omitted; confirm clean database initialization; remove hard-coded session secret and review password storage and access checks before real deployment. |
| Vue cinema web | Earlier frontend/backend project | `.env` omitted; historical password comment removed from the snapshot; add `.env.example` and schema setup. Review SQL interpolation and fixed JWT secret. Keep prototype limitations explicit. |

## Data/AI evidence

The existing `model_metrics.csv` reports Logistic Regression ROC-AUC approximately **0.668**, precision **0.375**, recall **0.035**, and F1 **0.064**. Random Forest ROC-AUC is approximately **0.605**. These are copied historical experiment results, not results reproduced during this preparation. Accuracy of 0.80 alone is insufficient evidence of useful no-show prediction on an imbalanced dataset.

The generator summary states 2,200 synthetic appointments and a 20.09% no-show rate. The training summary states a positive rate of about 19.64%. The discrepancy needs investigation; do not silently present them as a single consistent run. These synthetic experiments do not establish real clinical effectiveness.

## Archive policy

The small IDEA exercises and earlier cinema prototypes are preserved privately. Their purpose is learning history. They are not all candidates for profile pinning. Lecture materials and large course reports are not part of this source repository.
