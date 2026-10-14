from pathlib import Path
import json
import warnings

import joblib
import matplotlib.pyplot as plt
import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

warnings.filterwarnings("ignore", category=FutureWarning)

# =========================
# Paths
# =========================
ROOT = Path(__file__).resolve().parent
DATA_FILE = ROOT / "data" / "core" / "model_base_synthetic_expanded.csv"
OUTPUTS = ROOT / "outputs"
OUTPUTS.mkdir(parents=True, exist_ok=True)

# =========================
# Load data
# =========================
if not DATA_FILE.exists():
    raise FileNotFoundError(f"Cannot find data file: {DATA_FILE}")

df = pd.read_csv(DATA_FILE)

if "no_show" not in df.columns:
    raise ValueError("Target column 'no_show' not found in dataset.")

# =========================
# Columns to exclude
# =========================
leakage_or_id_cols = [
    "appointment_id",
    "patient_id",
    "doctor_id",
    "booking_date",
    "appointment_date",
    "no_show_prob_rule",
]

drop_cols = [c for c in leakage_or_id_cols if c in df.columns]

X = df.drop(columns=drop_cols + ["no_show"])
y = df["no_show"]

# Make sure target is numeric 0/1
if y.dtype == "object":
    y = y.astype(str).str.strip().str.lower().map(
        {"1": 1, "0": 0, "yes": 1, "no": 0, "true": 1, "false": 0}
    )

if y.isna().any():
    raise ValueError("Target column 'no_show' contains invalid or missing values after conversion.")

y = y.astype(int)

# =========================
# Detect numeric / categorical columns
# =========================
num_cols = X.select_dtypes(include=["number"]).columns.tolist()
cat_cols = [c for c in X.columns if c not in num_cols]

# Convert boolean-like columns if any slipped into object/string
for c in cat_cols:
    if X[c].dropna().astype(str).str.lower().isin(["true", "false"]).all():
        X[c] = X[c].astype(str).str.lower().map({"true": "true", "false": "false"})

# =========================
# Preprocessing
# =========================
numeric_pipe = Pipeline(
    steps=[
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
    ]
)

categorical_pipe = Pipeline(
    steps=[
        ("imputer", SimpleImputer(strategy="most_frequent")),
        ("onehot", OneHotEncoder(handle_unknown="ignore")),
    ]
)

preprocess = ColumnTransformer(
    transformers=[
        ("num", numeric_pipe, num_cols),
        ("cat", categorical_pipe, cat_cols),
    ]
)

# =========================
# Train / test split
# =========================
X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.2,
    random_state=42,
    stratify=y,
)

# =========================
# Candidate models + tuning grids
# =========================
candidates = {
    "LogisticRegression": (
        LogisticRegression(max_iter=3000, solver="liblinear"),
        {
            "model__C": [0.1, 1, 3, 10],
            "model__class_weight": [None, "balanced"],
        },
    ),
    "RandomForest": (
        RandomForestClassifier(random_state=42),
        {
            "model__n_estimators": [200, 400],
            "model__max_depth": [None, 8, 12],
            "model__min_samples_split": [2, 10],
            "model__class_weight": [None, "balanced"],
        },
    ),
}

results = []
best_model = None
best_name = None
best_auc = -1.0
best_search = None

# =========================
# Train and evaluate
# =========================
for name, (model, param_grid) in candidates.items():
    pipe = Pipeline(
        steps=[
            ("prep", preprocess),
            ("model", model),
        ]
    )

    search = GridSearchCV(
        estimator=pipe,
        param_grid=param_grid,
        scoring={"AUC": "roc_auc", "F1": "f1", "Recall": "recall"},
        refit="AUC",
        cv=5,
        n_jobs=-1,
        verbose=0,
    )

    search.fit(X_train, y_train)

    y_prob = search.predict_proba(X_test)[:, 1]
    y_pred = (y_prob >= 0.5).astype(int)

    metrics = {
        "model": name,
        "accuracy": float(accuracy_score(y_test, y_pred)),
        "precision": float(precision_score(y_test, y_pred, zero_division=0)),
        "recall": float(recall_score(y_test, y_pred, zero_division=0)),
        "f1": float(f1_score(y_test, y_pred, zero_division=0)),
        "roc_auc": float(roc_auc_score(y_test, y_prob)),
        "best_params": json.dumps(search.best_params_),
    }
    results.append(metrics)

    report_df = pd.DataFrame(
        classification_report(y_test, y_pred, output_dict=True)
    ).T
    report_df.to_csv(OUTPUTS / f"{name}_classification_report.csv", index=True)

    if metrics["roc_auc"] > best_auc:
        best_auc = metrics["roc_auc"]
        best_model = search.best_estimator_
        best_name = name
        best_search = search

# =========================
# Save model metrics
# =========================
results_df = pd.DataFrame(results).sort_values("roc_auc", ascending=False)
results_df.to_csv(OUTPUTS / "model_metrics.csv", index=False)

# =========================
# Save best model
# =========================
if best_model is None:
    raise RuntimeError("No model was successfully trained.")

joblib.dump(best_model, OUTPUTS / "best_model.joblib")

# =========================
# Predict full dataset for simulation use
# =========================
full_prob = best_model.predict_proba(X)[:, 1]
pred_df = df.copy()
pred_df["pred_no_show_prob"] = full_prob
pred_df.to_csv(OUTPUTS / "appointment_predictions.csv", index=False)

# =========================
# Plot model comparison
# =========================
plot_df = results_df.set_index("model")[["accuracy", "precision", "recall", "f1", "roc_auc"]]
ax = plot_df.plot(kind="bar", figsize=(10, 6))
ax.set_title("Model Performance Comparison")
ax.set_ylabel("Score")
ax.set_xlabel("")
plt.xticks(rotation=0)
plt.tight_layout()
plt.savefig(OUTPUTS / "model_comparison.png", dpi=300, bbox_inches="tight")
plt.close()

# =========================
# Export top features from best model
# =========================
top_features_path = OUTPUTS / "best_model_top_features.csv"

try:
    prep = best_model.named_steps["prep"]
    model = best_model.named_steps["model"]

    feature_names = prep.get_feature_names_out()

    if hasattr(model, "coef_"):
        importances = model.coef_[0]
    elif hasattr(model, "feature_importances_"):
        importances = model.feature_importances_
    else:
        importances = None

    if importances is not None:
        feat_df = pd.DataFrame(
            {
                "feature": feature_names,
                "importance": importances,
                "abs_importance": pd.Series(importances).abs(),
            }
        ).sort_values("abs_importance", ascending=False)

        feat_df.to_csv(top_features_path, index=False)

        top20 = feat_df.head(20).copy()
        ax = top20.sort_values("abs_importance").plot(
            x="feature",
            y="abs_importance",
            kind="barh",
            figsize=(10, 8),
            legend=False,
        )
        ax.set_title(f"Top 20 Features - {best_name}")
        ax.set_xlabel("Absolute Importance")
        ax.set_ylabel("")
        plt.tight_layout()
        plt.savefig(OUTPUTS / "best_model_top_features.png", dpi=300, bbox_inches="tight")
        plt.close()
except Exception as e:
    print(f"Warning: could not export feature importance plot. Reason: {e}")

# =========================
# Save training summary
# =========================
summary = {
    "data_file": str(DATA_FILE),
    "n_rows": int(len(df)),
    "n_features_before_encoding": int(X.shape[1]),
    "target_positive_rate": float(y.mean()),
    "train_size": int(len(X_train)),
    "test_size": int(len(X_test)),
    "best_model": best_name,
    "best_auc": float(best_auc),
}

with open(OUTPUTS / "training_summary.json", "w", encoding="utf-8") as f:
    json.dump(summary, f, indent=2)

# =========================
# Console output
# =========================
print("\nTraining completed successfully.")
print(f"Best model: {best_name}")
print(f"Best ROC-AUC: {best_auc:.4f}")
print("\nSaved files:")
print(f"- {OUTPUTS / 'model_metrics.csv'}")
print(f"- {OUTPUTS / 'model_comparison.png'}")
print(f"- {OUTPUTS / 'appointment_predictions.csv'}")
print(f"- {OUTPUTS / 'best_model.joblib'}")
print(f"- {OUTPUTS / 'training_summary.json'}")
if top_features_path.exists():
    print(f"- {top_features_path}")