# Appointment no-show modeling and scheduling simulation

An educational synthetic-data prototype connecting no-show classification with scheduling-policy simulation. It is a data/AI portfolio candidate that still needs reproducibility and evaluation work.

## Source files

- `generate_synthetic_appointments.py`: generates synthetic appointments from patient and doctor dimension tables.
- `train_model.py`: trains Logistic Regression and Random Forest pipelines and writes metrics/predictions.
- `simulate_schedule.py`: compares scheduling policies across scenarios.
- `tune_threshold.py`: evaluates configured thresholds; currently prefers a rule-generated probability if model predictions are absent.
- `dashboard_app.py`: Streamlit view of experiment results.
- `results/`: selected existing aggregate results, copied from the original project.

## Current limitations

Record-level input data, prediction rows, and the model binary are deliberately omitted from this preparation snapshot. The generator still requires patient/doctor input tables, so this repository is **not yet a reproducible end-to-end demo**. Required libraries include pandas, numpy, scikit-learn, matplotlib, joblib, and Streamlit; versions have not been pinned or verified here.

Existing ROC-AUC is about 0.668 for Logistic Regression and 0.605 for Random Forest. The reported Logistic Regression recall is about 0.035. These historical results are not a claim of clinical utility. A synthetic-label experiment must be described as such, with assumptions, class balance, baselines, held-out evaluation, and operational tradeoffs.

The dashboard expects files under `outputs/`, while the reviewed aggregate results here are stored under `results/`. Some referenced image names also differ from the existing plots. Data preparation, dependency setup, and dashboard reconciliation remain open work.
