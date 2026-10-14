import random
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd


# =========================
# Configuration
# =========================
PROJECT_ROOT = Path(__file__).resolve().parent

DATA_CORE = PROJECT_ROOT / "data" / "core"
SCENARIO_FILE = PROJECT_ROOT / "data" / "scenarios" / "scenarios.csv"
OUTPUT_DIR = PROJECT_ROOT / "outputs"

# 强制优先使用 synthetic expanded，因为它有 no_show_prob_rule
MODEL_BASE_FILE = DATA_CORE / "model_base_synthetic_expanded.csv"

THRESHOLDS = [0.50, 0.60, 0.70, 0.80, 0.90]
RUNS_PER_SCENARIO = 10
OVERBOOK_CAP_K = 1
RANDOM_SEED = 42

# Clinic working hours
WEEKDAY_OPEN_MIN = 9 * 60
WEEKDAY_CLOSE_MIN = 15 * 60
SATURDAY_OPEN_MIN = 9 * 60
SATURDAY_CLOSE_MIN = 12 * 60

# 你当前文件实际支持的列名
PROB_COL_CANDIDATES = [
    "predicted_no_show_prob",   # 后续如果你导出模型概率，可以优先用这个
    "no_show_prob_rule",        # 当前先用它
]

DOCTOR_COL_CANDIDATES = [
    "doctor_id",
]

DATE_COL_CANDIDATES = [
    "appointment_date",
]

TIME_COL_CANDIDATES = [
    "time_slot",
]

DEPARTMENT_COL_CANDIDATES = [
    "department",
]

SEVERITY_COL_CANDIDATES = [
    "severity",
    "case_severity",
    "severity_level",
]

FOLLOWUP_COL_CANDIDATES = [
    "is_follow_up",
    "appointment_type",
]

REMINDER_COL_CANDIDATES = [
    "reminder_sent",
]

WEEKDAY_COL_CANDIDATES = [
    "day_of_week",
]


# =========================
# Utility helpers
# =========================
def first_existing_column(df: pd.DataFrame, candidates: List[str]) -> Optional[str]:
    for c in candidates:
        if c in df.columns:
            return c
    return None


def ensure_outputs_dir() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def load_model_base() -> pd.DataFrame:
    if not MODEL_BASE_FILE.exists():
        raise FileNotFoundError(f"Model base file not found: {MODEL_BASE_FILE}")
    df = pd.read_csv(MODEL_BASE_FILE)
    print(f"Loaded model base: {MODEL_BASE_FILE.name}")
    return df


def load_scenarios() -> pd.DataFrame:
    if not SCENARIO_FILE.exists():
        raise FileNotFoundError(f"Scenario file not found: {SCENARIO_FILE}")
    df = pd.read_csv(SCENARIO_FILE)
    print(f"Loaded scenarios: {SCENARIO_FILE.name}")
    return df


def clamp(x: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, x))


def parse_time_to_minutes(v) -> int:
    """
    支持:
    - 09:00
    - 09:00:00
    - 'morning' / 'afternoon' / 'evening'
    - 纯数字
    """
    if pd.isna(v):
        return WEEKDAY_OPEN_MIN

    if isinstance(v, (int, float)):
        if 0 <= v <= 24 * 60:
            return int(v)
        if 0 <= v <= 24:
            return int(v * 60)

    s = str(v).strip().lower()

    # 你现在 time_slot 可能是这种类别值
    slot_map = {
        "morning": 9 * 60,
        "late_morning": 10 * 60 + 30,
        "noon": 12 * 60,
        "afternoon": 13 * 60 + 30,
        "late_afternoon": 14 * 60 + 30,
        "evening": 17 * 60,
    }
    if s in slot_map:
        return slot_map[s]

    # HH:MM / HH:MM:SS
    parts = s.split(":")
    if len(parts) >= 2:
        try:
            hh = int(parts[0])
            mm = int(parts[1])
            return hh * 60 + mm
        except Exception:
            pass

    try:
        dt = pd.to_datetime(s)
        return int(dt.hour * 60 + dt.minute)
    except Exception:
        pass

    return WEEKDAY_OPEN_MIN


def normalize_yes_no(value) -> bool:
    if pd.isna(value):
        return False
    s = str(value).strip().lower()
    return s in {"1", "true", "yes", "y", "sent"}


def get_weekday_name(row: pd.Series, date_col: Optional[str], weekday_col: Optional[str]) -> str:
    if weekday_col and pd.notna(row.get(weekday_col)):
        return str(row[weekday_col]).strip().lower()

    if date_col and pd.notna(row.get(date_col)):
        try:
            d = pd.to_datetime(row[date_col])
            return d.day_name().lower()
        except Exception:
            pass

    return "monday"


def get_session_bounds(weekday_name: str) -> Tuple[int, int]:
    if "sat" in weekday_name:
        return SATURDAY_OPEN_MIN, SATURDAY_CLOSE_MIN
    return WEEKDAY_OPEN_MIN, WEEKDAY_CLOSE_MIN


# =========================
# Data preparation
# =========================
def prepare_dataframe(df: pd.DataFrame) -> Tuple[pd.DataFrame, Dict[str, Optional[str]]]:
    prob_col = first_existing_column(df, PROB_COL_CANDIDATES)
    doctor_col = first_existing_column(df, DOCTOR_COL_CANDIDATES)
    date_col = first_existing_column(df, DATE_COL_CANDIDATES)
    time_col = first_existing_column(df, TIME_COL_CANDIDATES)
    dept_col = first_existing_column(df, DEPARTMENT_COL_CANDIDATES)
    severity_col = first_existing_column(df, SEVERITY_COL_CANDIDATES)
    followup_col = first_existing_column(df, FOLLOWUP_COL_CANDIDATES)
    reminder_col = first_existing_column(df, REMINDER_COL_CANDIDATES)
    weekday_col = first_existing_column(df, WEEKDAY_COL_CANDIDATES)

    missing = []
    if prob_col is None:
        missing.append("predicted / risk probability column")
    if doctor_col is None:
        missing.append("doctor column")
    if date_col is None:
        missing.append("date column")
    if time_col is None:
        missing.append("time column")

    if missing:
        raise ValueError(
            "Missing required columns in model base:\n- "
            + "\n- ".join(missing)
        )

    work = df.copy()

    work[prob_col] = pd.to_numeric(work[prob_col], errors="coerce").fillna(0.20).clip(0.01, 0.99)
    work["__time_min"] = work[time_col].apply(parse_time_to_minutes)
    work["__weekday_name"] = work.apply(lambda r: get_weekday_name(r, date_col, weekday_col), axis=1)

    if dept_col is None:
        work["__department_norm"] = "general"
    else:
        work["__department_norm"] = work[dept_col].fillna("general").astype(str).str.lower()

    # severity：你当前文件没有，就用 appointment_type + follow_up 辅助近似
    if severity_col is None:
        def infer_severity(row):
            appt_type = str(row.get("appointment_type", "")).strip().lower()
            if row["__is_follow_up"]:
                return "followup"
            if "surgery" in appt_type or "consultation" in appt_type:
                return "severe"
            if "new" in appt_type:
                return "moderate"
            return "mild"
        work["__severity_norm"] = "moderate"
    else:
        work["__severity_norm"] = work[severity_col].fillna("moderate").astype(str).str.lower()

    # follow-up
    if followup_col is None:
        work["__is_follow_up"] = False
    else:
        if work[followup_col].dtype == bool:
            work["__is_follow_up"] = work[followup_col].fillna(False)
        else:
            work["__is_follow_up"] = work[followup_col].apply(
                lambda x: str(x).strip().lower() in {"follow_up", "follow-up", "followup", "review", "recheck"}
                or normalize_yes_no(x)
            )

    # reminder
    if reminder_col is None:
        work["__has_reminder"] = False
    else:
        if work[reminder_col].dtype == bool:
            work["__has_reminder"] = work[reminder_col].fillna(False)
        else:
            work["__has_reminder"] = work[reminder_col].apply(normalize_yes_no)

    # 若没有 severity 真字段，则用 appointment_type 再次修正
    if severity_col is None:
        def infer_severity_from_type(row):
            appt_type = str(row.get("appointment_type", "")).strip().lower()
            if row["__is_follow_up"]:
                return "followup"
            if "consult" in appt_type or "surgery" in appt_type:
                return "severe"
            if "new" in appt_type:
                return "moderate"
            return "mild"
        work["__severity_norm"] = work.apply(infer_severity_from_type, axis=1)

    meta = {
        "prob_col": prob_col,
        "doctor_col": doctor_col,
        "date_col": date_col,
        "time_col": time_col,
        "dept_col": dept_col,
        "severity_col": severity_col,
        "followup_col": followup_col,
        "reminder_col": reminder_col,
        "weekday_col": weekday_col,
    }
    return work, meta


# =========================
# Scenario adjustment
# =========================
def get_scenario_no_show_rate(scen_row: pd.Series, global_base_rate: float) -> float:
    for c in ["no_show_rate", "target_no_show_rate", "base_no_show_rate"]:
        if c in scen_row.index and pd.notna(scen_row[c]):
            v = float(scen_row[c])
            if v > 1:
                v = v / 100.0
            return clamp(v, 0.01, 0.95)
    return global_base_rate


def adjust_probability_for_scenario(
    base_prob: float,
    row: pd.Series,
    scen_row: pd.Series,
    global_base_rate: float,
) -> float:
    p = float(base_prob)

    target_rate = get_scenario_no_show_rate(scen_row, global_base_rate)
    p += (target_rate - global_base_rate)

    weather = str(scen_row.get("weather_condition", "")).strip().lower()
    if "rain" in weather or "storm" in weather or "bad" in weather:
        p += 0.05

    pressure = str(scen_row.get("time_pressure_level", "")).strip().lower()
    if pressure in {"high", "peak", "heavy"}:
        p += 0.03

    dept_bias = str(scen_row.get("department_bias", "")).strip().lower()
    if dept_bias and dept_bias != "nan":
        if dept_bias in row["__department_norm"]:
            p += 0.04

    scen_name = str(scen_row.get("scenario_name", "")).strip().lower()
    weekday_name = row["__weekday_name"]
    t = row["__time_min"]

    if "monday" in scen_name and "morning" in scen_name:
        if "monday" in weekday_name and t < 11 * 60:
            p += 0.06

    if "friday" in scen_name and "afternoon" in scen_name:
        if "friday" in weekday_name and t >= 13 * 60:
            p += 0.06

    if "long lead" in scen_name or "lead-time" in scen_name:
        p += 0.03

    if "high no-show" in scen_name or "stress" in scen_name:
        p += 0.06

    if "low no-show" in scen_name:
        p -= 0.05

    if row["__is_follow_up"]:
        p -= 0.03

    if row["__has_reminder"]:
        p -= 0.04

    sev = row["__severity_norm"]
    if "severe" in sev or "surgery" in sev:
        p -= 0.03
    elif "mild" in sev:
        p += 0.01

    return clamp(p, 0.01, 0.99)


# =========================
# Service time model
# =========================
def sample_service_minutes(row: pd.Series, rng: random.Random, is_extra: bool = False) -> int:
    if is_extra:
        choices = [
            ("followup", 10, 0.30),
            ("mild", 12, 0.35),
            ("moderate", 18, 0.25),
            ("severe", 25, 0.10),
        ]
        r = rng.random()
        cum = 0.0
        base = 15
        for _, mins, w in choices:
            cum += w
            if r <= cum:
                base = mins
                break
        noise = rng.randint(-2, 3)
        return max(6, base + noise)

    sev = row["__severity_norm"]

    if "followup" in sev:
        base = 10
    elif "mild" in sev:
        base = 12
    elif "moderate" in sev:
        base = 18
    elif "severe" in sev:
        base = 28
    elif "surgery" in sev:
        base = 35
    else:
        base = 18

    noise = rng.randint(-3, 4)
    return max(6, base + noise)


# =========================
# Simulation core
# =========================
def simulate_one_session(
    sess_df: pd.DataFrame,
    tau: float,
    k_cap: int,
    scen_row: pd.Series,
    global_base_rate: float,
    rng: random.Random,
    prob_col: str,
) -> Dict[str, float]:
    weekday_name = str(sess_df["__weekday_name"].iloc[0]).lower()
    open_min, close_min = get_session_bounds(weekday_name)
    capacity = close_min - open_min

    rows = []
    for _, row in sess_df.iterrows():
        adj_p = adjust_probability_for_scenario(row[prob_col], row, scen_row, global_base_rate)
        rows.append({
            "scheduled_min": int(row["__time_min"]),
            "adj_no_show_prob": adj_p,
            "service_min": sample_service_minutes(row, rng, is_extra=False),
            "is_extra": False,
        })

    overbookable = sorted(rows, key=lambda x: x["adj_no_show_prob"], reverse=True)
    chosen = [r for r in overbookable if r["adj_no_show_prob"] >= tau][:k_cap]

    for chosen_row in chosen:
        rows.append({
            "scheduled_min": chosen_row["scheduled_min"],
            "adj_no_show_prob": 0.15,
            "service_min": sample_service_minutes(pd.Series(dtype=object), rng, is_extra=True),
            "is_extra": True,
        })

    rows.sort(key=lambda x: (x["scheduled_min"], x["is_extra"]))

    current_time = open_min
    busy_time = 0
    overtime = 0
    idle_time = 0
    waits = []
    attended = 0

    for item in rows:
        scheduled = item["scheduled_min"]

        if current_time < scheduled:
            idle_gap = min(scheduled, close_min) - current_time
            if idle_gap > 0:
                idle_time += idle_gap
            current_time = scheduled

        show_prob = 1.0 - item["adj_no_show_prob"]
        show = rng.random() < show_prob

        if not show:
            continue

        start = max(current_time, scheduled)
        wait = max(0, start - scheduled)
        end = start + item["service_min"]

        waits.append(wait)
        attended += 1
        busy_time += item["service_min"]
        current_time = end

    if current_time < close_min:
        idle_time += (close_min - current_time)
    else:
        overtime += (current_time - close_min)

    utilization = min(busy_time, capacity) / capacity if capacity > 0 else 0.0
    avg_wait = float(np.mean(waits)) if waits else 0.0

    return {
        "utilization": utilization,
        "attended_patients": float(attended),
        "avg_waiting_time": avg_wait,
        "overtime": float(overtime),
        "idle_time": float(idle_time),
    }


def simulate_threshold_across_scenario(
    df: pd.DataFrame,
    scen_row: pd.Series,
    tau: float,
    k_cap: int,
    runs: int,
    meta: Dict[str, Optional[str]],
) -> Dict[str, float]:
    prob_col = meta["prob_col"]
    doctor_col = meta["doctor_col"]
    date_col = meta["date_col"]

    global_base_rate = float(df[prob_col].mean())

    metrics = {
        "utilization": [],
        "attended_patients": [],
        "avg_waiting_time": [],
        "overtime": [],
        "idle_time": [],
    }

    for run_idx in range(runs):
        rng = random.Random(RANDOM_SEED + run_idx + int(tau * 1000))

        grouped = df.groupby([doctor_col, date_col], dropna=False)

        run_values = {
            "utilization": [],
            "attended_patients": [],
            "avg_waiting_time": [],
            "overtime": [],
            "idle_time": [],
        }

        for _, sess_df in grouped:
            session_result = simulate_one_session(
                sess_df=sess_df,
                tau=tau,
                k_cap=k_cap,
                scen_row=scen_row,
                global_base_rate=global_base_rate,
                rng=rng,
                prob_col=prob_col,
            )
            for k, v in session_result.items():
                run_values[k].append(v)

        for k in metrics.keys():
            metrics[k].append(float(np.mean(run_values[k])) if run_values[k] else 0.0)

    return {
        "utilization": float(np.mean(metrics["utilization"])),
        "attended_patients": float(np.mean(metrics["attended_patients"])),
        "avg_waiting_time": float(np.mean(metrics["avg_waiting_time"])),
        "overtime": float(np.mean(metrics["overtime"])),
        "idle_time": float(np.mean(metrics["idle_time"])),
    }


# =========================
# Main
# =========================
def main():
    ensure_outputs_dir()

    base_df = load_model_base()
    scenarios_df = load_scenarios()
    base_df, meta = prepare_dataframe(base_df)

    print(f"Rows in model base: {len(base_df)}")
    print(f"Scenarios: {len(scenarios_df)}")
    print(f"Runs per scenario: {RUNS_PER_SCENARIO}")
    print(f"Thresholds: {THRESHOLDS}")
    print(f"Overbooking cap K: {OVERBOOK_CAP_K}")
    print(f"Probability column used: {meta['prob_col']}")
    print(f"Time column used: {meta['time_col']}")

    by_scenario_records = []

    for _, scen_row in scenarios_df.iterrows():
        scen_id = scen_row.get("scenario_id", "")
        scen_name = str(scen_row.get("scenario_name", f"scenario_{scen_id}"))

        print(f"\nRunning scenario: {scen_name}")

        for tau in THRESHOLDS:
            result = simulate_threshold_across_scenario(
                df=base_df,
                scen_row=scen_row,
                tau=tau,
                k_cap=OVERBOOK_CAP_K,
                runs=RUNS_PER_SCENARIO,
                meta=meta,
            )

            row = {
                "scenario_id": scen_id,
                "scenario_name": scen_name,
                "threshold_tau": tau,
                "utilization": round(result["utilization"], 4),
                "attended_patients": round(result["attended_patients"], 2),
                "avg_waiting_time": round(result["avg_waiting_time"], 2),
                "overtime": round(result["overtime"], 2),
                "idle_time": round(result["idle_time"], 2),
            }
            by_scenario_records.append(row)

            print(
                f"  tau={tau:.2f} | util={row['utilization']:.4f} | "
                f"attended={row['attended_patients']:.2f} | wait={row['avg_waiting_time']:.2f} | "
                f"ot={row['overtime']:.2f} | idle={row['idle_time']:.2f}"
            )

    by_scenario_df = pd.DataFrame(by_scenario_records)

    result_df = (
        by_scenario_df.groupby("threshold_tau", as_index=False)[
            ["utilization", "attended_patients", "avg_waiting_time", "overtime", "idle_time"]
        ]
        .mean()
        .sort_values("threshold_tau")
    )

    result_df["utilization"] = result_df["utilization"].round(4)
    result_df["attended_patients"] = result_df["attended_patients"].round(2)
    result_df["avg_waiting_time"] = result_df["avg_waiting_time"].round(2)
    result_df["overtime"] = result_df["overtime"].round(2)
    result_df["idle_time"] = result_df["idle_time"].round(2)

    by_scenario_path = OUTPUT_DIR / "threshold_tuning_by_scenario.csv"
    result_path = OUTPUT_DIR / "threshold_tuning_results.csv"

    by_scenario_df.to_csv(by_scenario_path, index=False)
    result_df.to_csv(result_path, index=False)

    print("\nSaved:")
    print(f"- {by_scenario_path}")
    print(f"- {result_path}")

    print("\nUse threshold_tuning_results.csv for Table 8.X+1 in your report.")


if __name__ == "__main__":
    main()