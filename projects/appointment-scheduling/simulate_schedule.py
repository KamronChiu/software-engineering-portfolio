from pathlib import Path
import json
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# =========================
# Paths
# =========================
ROOT = Path(__file__).resolve().parent
PRED_FILE = ROOT / "outputs" / "appointment_predictions.csv"
SCENARIO_FILE = ROOT / "data" / "scenarios" / "scenarios.csv"
DOCTOR_FILE = ROOT / "data" / "core" / "doctors_clean.csv"
SLOT_FILE = ROOT / "data" / "core" / "slots_clean.csv"
OUTPUTS = ROOT / "outputs"
OUTPUTS.mkdir(parents=True, exist_ok=True)

# =========================
# Config
# =========================
N_RUNS_PER_SCENARIO = 10
RANDOM_SEED = 42

# clinic hours
BASE_START_MIN = 9 * 60          # 09:00
WEEKDAY_END_MIN = 15 * 60        # 15:00
SATURDAY_END_MIN = 12 * 60       # 12:00

# fallback service time if doctor avg unavailable
DEFAULT_SERVICE_MIN = 20

# arrival realism
MIN_ARRIVAL_SHIFT = -8           # can arrive 8 min early
MAX_ARRIVAL_SHIFT = 25           # or 25 min late

# severity/service realism
MIN_SERVICE_TIME = 6
MAX_SERVICE_TIME = 80

# walk-in add-on realism
BASE_WALKIN_LAMBDA = 0.35        # baseline average walk-ins per doctor-day
WALKIN_HIGH_PRESSURE_BONUS = 0.45
WALKIN_MONDAY_BONUS = 0.25
WALKIN_RAIN_PENALTY = -0.10

np.random.seed(RANDOM_SEED)

# =========================
# Helpers
# =========================
def clip_prob(x):
    return float(np.clip(x, 0.01, 0.95))

def time_to_min(t):
    try:
        hh, mm = str(t).split(":")
        return int(hh) * 60 + int(mm)
    except Exception:
        return BASE_START_MIN

def min_to_hhmm(x):
    x = int(round(x))
    hh = max(0, x // 60)
    mm = max(0, x % 60)
    return f"{hh:02d}:{mm:02d}"

def find_first_existing(cols, candidates):
    for c in candidates:
        if c in cols:
            return c
    return None

def get_day_window(date_value):
    """
    Mon-Fri: 09:00-15:00
    Sat:     09:00-12:00
    """
    if pd.isna(date_value):
        return BASE_START_MIN, WEEKDAY_END_MIN

    weekday = pd.Timestamp(date_value).day_name().lower()
    if weekday == "saturday":
        return BASE_START_MIN, SATURDAY_END_MIN
    return BASE_START_MIN, WEEKDAY_END_MIN

def scenario_overbook_threshold(target_no_show_rate):
    """
    Lower threshold in higher no-show scenarios,
    so optimized policy becomes more active.
    """
    r = float(target_no_show_rate)
    if r >= 0.30:
        return 0.18
    elif r >= 0.20:
        return 0.22
    elif r >= 0.10:
        return 0.26
    else:
        return 0.30

def scenario_max_extras_per_doctor_day(target_no_show_rate):
    """
    Cap how many extra overbooked appointments can be added per doctor-day.
    """
    r = float(target_no_show_rate)
    if r >= 0.30:
        return 4
    elif r >= 0.20:
        return 3
    elif r >= 0.10:
        return 2
    else:
        return 1

def infer_service_severity(row):
    """
    Infer service severity from appointment_type / follow-up / optional existing fields.
    Categories:
    - minor_followup
    - standard
    - major_complex
    """
    if "severity_level" in row.index and pd.notna(row["severity_level"]):
        sev = str(row["severity_level"]).strip().lower()
        if sev in ["minor", "mild", "followup", "follow-up"]:
            return "minor_followup"
        elif sev in ["major", "severe", "complex", "surgery"]:
            return "major_complex"
        else:
            return "standard"

    appointment_type = str(row.get("appointment_type", "")).strip().lower()

    try:
        is_follow_up = int(row.get("is_follow_up", 0))
    except Exception:
        is_follow_up = 0

    score = 0

    # shorter visits
    if is_follow_up == 1:
        score -= 2
    if any(k in appointment_type for k in ["follow", "review", "recheck", "check", "routine", "refill"]):
        score -= 2

    # longer visits
    if any(k in appointment_type for k in ["surgery", "operation", "procedure", "consult", "plan", "new", "specialist"]):
        score += 2

    if "chronic_disease" in row.index and pd.notna(row["chronic_disease"]):
        try:
            if int(row["chronic_disease"]) == 1:
                score += 1
        except Exception:
            pass

    if score <= -1:
        return "minor_followup"
    elif score >= 2:
        return "major_complex"
    else:
        return "standard"

def sample_service_time_from_row(row):
    """
    Service time depends on severity and doctor average service time.
    - minor_followup: shorter
    - standard: around doctor average
    - major_complex: much longer
    """
    avg = float(row.get("avg_service_time", DEFAULT_SERVICE_MIN))
    sev = infer_service_severity(row)

    if sev == "minor_followup":
        mean = max(8, avg * 0.65)
        std = max(2, mean * 0.18)
        low, high = 6, max(18, avg * 1.0)
    elif sev == "major_complex":
        mean = max(22, avg * 1.75)
        std = max(4, mean * 0.15)
        low, high = max(18, avg * 1.1), min(MAX_SERVICE_TIME, avg * 3.0)
    else:
        mean = avg * 1.0
        std = max(3, mean * 0.15)
        low, high = max(10, avg * 0.6), min(MAX_SERVICE_TIME, avg * 1.8)

    val = np.random.normal(loc=mean, scale=std)
    return float(np.clip(val, low, high))

def sample_arrival_shift(row, scenario_row):
    """
    More realistic arrival pattern:
    - rainy / high pressure / Monday morning => later
    - otherwise near on-time
    """
    mean_shift = 0.0
    std_shift = 4.0

    weather_condition = str(scenario_row.get("weather_condition", "normal")).strip().lower()
    time_pressure = str(scenario_row.get("time_pressure_level", "normal")).strip().lower()
    day_of_week = str(row.get("day_of_week", "")).strip().lower()
    time_slot = str(row.get("time_slot", ""))

    if weather_condition in ["rainy", "storm", "bad_weather"]:
        mean_shift += 4.0
        std_shift += 1.0

    if time_pressure == "high":
        mean_shift += 2.0

    if day_of_week == "monday" and time_slot in ["09:00", "09:30", "10:00"]:
        mean_shift += 2.0

    if day_of_week == "friday" and time_slot in ["14:00", "14:30"]:
        mean_shift += 1.5

    val = np.random.normal(loc=mean_shift, scale=std_shift)
    return float(np.clip(val, MIN_ARRIVAL_SHIFT, MAX_ARRIVAL_SHIFT))

def apply_scenario_adjustment(row, scenario_row, global_current_mean):
    """
    Start from model-predicted no-show probability,
    then apply scenario-level shifts.
    """
    p = float(row["pred_no_show_prob"])

    # shift toward target scenario no-show rate
    target = float(scenario_row["no_show_rate"])
    p += (target - global_current_mean)

    # weather shock
    weather_condition = str(scenario_row.get("weather_condition", "normal")).strip().lower()
    row_weather = str(row.get("weather_type", "normal")).strip().lower()
    if weather_condition in ["rainy", "storm", "bad_weather"]:
        p += 0.04
        if row_weather in ["rainy", "storm"]:
            p += 0.04

    # time pressure
    time_pressure = str(scenario_row.get("time_pressure_level", "normal")).strip().lower()
    row_time = str(row.get("time_slot", ""))
    if time_pressure == "high" and row_time in ["09:00", "09:30", "10:00", "14:00", "14:30"]:
        p += 0.03

    # department-specific bias
    dep_bias = str(scenario_row.get("department_bias", "")).strip().lower()
    row_dep = str(row.get("department", "")).strip().lower()
    if dep_bias and row_dep == dep_bias:
        p += 0.05

    # Monday morning spike
    day_of_week = str(row.get("day_of_week", "")).strip().lower()
    if day_of_week == "monday" and row_time in ["09:00", "09:30", "10:00"]:
        p += 0.03

    # long lead time
    lead_time = row.get("lead_time_days", None)
    if pd.notna(lead_time):
        try:
            if float(lead_time) >= 14:
                p += 0.03
        except Exception:
            pass

    # reminder effect
    reminder_sent = row.get("reminder_sent", None)
    if pd.notna(reminder_sent):
        try:
            if int(reminder_sent) == 1:
                p -= 0.03
        except Exception:
            pass

    return clip_prob(p)

# =========================
# Load data
# =========================
if not PRED_FILE.exists():
    raise FileNotFoundError(f"Cannot find prediction file: {PRED_FILE}")
if not SCENARIO_FILE.exists():
    raise FileNotFoundError(f"Cannot find scenario file: {SCENARIO_FILE}")
if not DOCTOR_FILE.exists():
    raise FileNotFoundError(f"Cannot find doctor file: {DOCTOR_FILE}")

pred = pd.read_csv(PRED_FILE)
scenarios = pd.read_csv(SCENARIO_FILE)
doctors = pd.read_csv(DOCTOR_FILE)

slots = None
if SLOT_FILE.exists():
    try:
        slots = pd.read_csv(SLOT_FILE)
    except Exception:
        slots = None

# =========================
# Basic checks / conversions
# =========================
required_pred_cols = ["doctor_id", "appointment_date", "time_slot", "pred_no_show_prob"]
for c in required_pred_cols:
    if c not in pred.columns:
        raise ValueError(f"Required column '{c}' not found in appointment_predictions.csv")

pred["appointment_date"] = pd.to_datetime(pred["appointment_date"], errors="coerce")

# Add avg_service_time if missing
if "avg_service_time" not in pred.columns:
    if "doctor_id" in doctors.columns and "avg_service_time" in doctors.columns:
        pred = pred.merge(
            doctors[["doctor_id", "avg_service_time"]],
            on="doctor_id",
            how="left"
        )

if "avg_service_time" not in pred.columns:
    pred["avg_service_time"] = DEFAULT_SERVICE_MIN

pred["avg_service_time"] = pd.to_numeric(pred["avg_service_time"], errors="coerce").fillna(DEFAULT_SERVICE_MIN)

# Scenario compatibility
if "scenario_id" not in scenarios.columns:
    scenarios["scenario_id"] = [f"S{i+1}" for i in range(len(scenarios))]
if "scenario_name" not in scenarios.columns:
    scenarios["scenario_name"] = scenarios["scenario_id"]

if "no_show_rate" not in scenarios.columns:
    raise ValueError("scenarios.csv must contain 'no_show_rate' column.")

for optional_col, default_val in {
    "weather_condition": "normal",
    "time_pressure_level": "normal",
    "department_bias": "",
    "note": ""
}.items():
    if optional_col not in scenarios.columns:
        scenarios[optional_col] = default_val

# If missing in predictions, create defaults
if "weather_type" not in pred.columns:
    pred["weather_type"] = "normal"
if "day_of_week" not in pred.columns:
    pred["day_of_week"] = pred["appointment_date"].dt.day_name().str.lower()

# Optional slot capacity reference
pred["base_slot_capacity"] = 1
if slots is not None and not slots.empty:
    slot_cols = slots.columns.tolist()
    slot_date_col = find_first_existing(slot_cols, ["appointment_date", "slot_date", "date"])
    slot_time_col = find_first_existing(slot_cols, ["time_slot", "slot_time", "start_time"])
    slot_capacity_col = find_first_existing(slot_cols, ["capacity", "slot_capacity", "max_capacity"])

    if "doctor_id" in slot_cols and slot_date_col and slot_time_col:
        slot_map = slots.copy()
        slot_map[slot_date_col] = pd.to_datetime(slot_map[slot_date_col], errors="coerce")
        if slot_capacity_col is None:
            slot_map["__slot_capacity__"] = 1
            slot_capacity_col = "__slot_capacity__"

        slot_map = slot_map[["doctor_id", slot_date_col, slot_time_col, slot_capacity_col]].copy()
        slot_map.columns = ["doctor_id", "appointment_date", "time_slot", "base_slot_capacity"]

        pred = pred.merge(
            slot_map,
            on=["doctor_id", "appointment_date", "time_slot"],
            how="left",
            suffixes=("", "_slot")
        )
        pred["base_slot_capacity"] = pd.to_numeric(pred["base_slot_capacity"], errors="coerce").fillna(1)

# =========================
# Policy assignment
# =========================
def assign_baseline_policy(df):
    out = df.copy()
    out["policy"] = "baseline"
    out["is_overbooked_extra"] = 0
    out["source"] = out.get("source", "scheduled")
    return out

def assign_optimized_policy(df, scenario_row):
    """
    More aggressive overbooking under higher no-show scenarios.
    """
    out = df.copy()
    out["policy"] = "optimized"
    out["is_overbooked_extra"] = 0
    out["source"] = out.get("source", "scheduled")

    threshold = scenario_overbook_threshold(scenario_row["no_show_rate"])
    max_extra_per_doc_day = scenario_max_extras_per_doctor_day(scenario_row["no_show_rate"])

    eligible = out[out["p_final"] >= threshold].copy()
    if eligible.empty:
        return out

    eligible["slot_key"] = (
        eligible["doctor_id"].astype(str)
        + "|"
        + eligible["appointment_date"].astype(str)
        + "|"
        + eligible["time_slot"].astype(str)
    )

    eligible = (
        eligible.sort_values(["doctor_id", "appointment_date", "p_final"], ascending=[True, True, False])
        .drop_duplicates(subset=["slot_key"])
    )

    extra_rows = []
    for (doctor_id, appt_date), group in eligible.groupby(["doctor_id", "appointment_date"]):
        chosen = group.head(max_extra_per_doc_day).copy()
        if chosen.empty:
            continue

        chosen["is_overbooked_extra"] = 1
        chosen["source"] = "overbooked_extra"

        if "appointment_id" in chosen.columns:
            chosen["appointment_id"] = chosen["appointment_id"].astype(str) + "_OB"

        # extra appointment behaves like a newly inserted patient
        chosen["show"] = np.random.binomial(1, 1 - chosen["p_final"])
        chosen["arrival_shift_min"] = chosen.apply(lambda r: sample_arrival_shift(r, scenario_row), axis=1)
        chosen["scheduled_minute"] = chosen["time_slot"].apply(time_to_min)
        chosen["arrival_minute"] = chosen["scheduled_minute"] + chosen["arrival_shift_min"]
        chosen["service_time_realized"] = chosen.apply(sample_service_time_from_row, axis=1)

        extra_rows.append(chosen)

    if extra_rows:
        out = pd.concat([out] + extra_rows, ignore_index=True)

    return out

# =========================
# Walk-in add-on generation
# =========================
def estimate_walkin_lambda(appt_date, scenario_row):
    """
    Walk-ins depend on day and scenario pressure.
    """
    lam = BASE_WALKIN_LAMBDA
    weekday = pd.Timestamp(appt_date).day_name().lower()

    if weekday == "monday":
        lam += WALKIN_MONDAY_BONUS

    if str(scenario_row.get("time_pressure_level", "normal")).strip().lower() == "high":
        lam += WALKIN_HIGH_PRESSURE_BONUS

    if str(scenario_row.get("weather_condition", "normal")).strip().lower() in ["rainy", "storm", "bad_weather"]:
        lam += WALKIN_RAIN_PENALTY

    if weekday == "saturday":
        lam = max(0.15, lam * 0.7)

    return max(0.05, lam)

def build_walkin_row(doctor_id, appt_date, doctor_department, doctor_avg_service_time, idx):
    day_start, day_end = get_day_window(appt_date)
    latest_start = max(day_start + 10, day_end - 20)
    arrival_minute = np.random.randint(day_start + 5, latest_start + 1)

    # walk-ins are usually light or standard; small chance of complex
    walkin_type = np.random.choice(
        ["walk_in_minor", "walk_in_standard", "walk_in_complex"],
        p=[0.55, 0.35, 0.10]
    )

    if walkin_type == "walk_in_minor":
        severity_level = "minor"
        is_follow_up = 0
        avg_service = max(10, doctor_avg_service_time * 0.65)
        appointment_type = "walk-in minor complaint"
    elif walkin_type == "walk_in_complex":
        severity_level = "major"
        is_follow_up = 0
        avg_service = max(24, doctor_avg_service_time * 1.6)
        appointment_type = "walk-in complex consultation"
    else:
        severity_level = "standard"
        is_follow_up = 0
        avg_service = doctor_avg_service_time
        appointment_type = "walk-in standard visit"

    row = {
        "appointment_id": f"WALKIN_{doctor_id}_{pd.Timestamp(appt_date).strftime('%Y%m%d')}_{idx}",
        "patient_id": f"W_PAT_{idx}",
        "doctor_id": doctor_id,
        "appointment_date": appt_date,
        "booking_date": appt_date,
        "department": doctor_department,
        "time_slot": min_to_hhmm(arrival_minute),
        "scheduled_minute": float(arrival_minute),
        "arrival_shift_min": 0.0,
        "arrival_minute": float(arrival_minute),
        "pred_no_show_prob": 0.02,
        "p_final": 0.02,
        "show": 1,
        "source": "walk_in",
        "is_overbooked_extra": 0,
        "appointment_type": appointment_type,
        "is_follow_up": is_follow_up,
        "severity_level": severity_level,
        "avg_service_time": float(avg_service),
        "service_time_realized": np.nan,  # fill later
        "day_of_week": pd.Timestamp(appt_date).day_name().lower(),
        "weather_type": "normal",
        "base_slot_capacity": 1,
    }
    return row

def generate_walkins(sim_df, scenario_row, doctors_df):
    """
    Generate walk-in add-ons per doctor-day.
    """
    doctor_dept_col = "department" if "department" in doctors_df.columns else None
    doctor_avg_col = "avg_service_time" if "avg_service_time" in doctors_df.columns else None

    doctor_info = doctors_df.copy()
    if doctor_dept_col is None:
        doctor_info["department"] = "general"
        doctor_dept_col = "department"
    if doctor_avg_col is None:
        doctor_info["avg_service_time"] = DEFAULT_SERVICE_MIN
        doctor_avg_col = "avg_service_time"

    doc_lookup = doctor_info.set_index("doctor_id")[[doctor_dept_col, doctor_avg_col]].to_dict("index")

    unique_doc_days = sim_df[["doctor_id", "appointment_date"]].drop_duplicates().copy()
    if unique_doc_days.empty:
        return pd.DataFrame()

    walkin_rows = []
    counter = 0

    for _, row in unique_doc_days.iterrows():
        doctor_id = row["doctor_id"]
        appt_date = row["appointment_date"]

        if pd.isna(appt_date):
            continue

        lam = estimate_walkin_lambda(appt_date, scenario_row)
        n_walkins = np.random.poisson(lam)

        if n_walkins <= 0:
            continue

        info = doc_lookup.get(doctor_id, {})
        department = info.get(doctor_dept_col, "general")
        avg_service = float(info.get(doctor_avg_col, DEFAULT_SERVICE_MIN))

        for i in range(n_walkins):
            counter += 1
            walkin_rows.append(
                build_walkin_row(
                    doctor_id=doctor_id,
                    appt_date=appt_date,
                    doctor_department=department,
                    doctor_avg_service_time=avg_service,
                    idx=counter,
                )
            )

    if not walkin_rows:
        return pd.DataFrame()

    walkins = pd.DataFrame(walkin_rows)
    walkins["service_time_realized"] = walkins.apply(sample_service_time_from_row, axis=1)
    return walkins

# =========================
# Simulation engine
# =========================
def simulate_policy(schedule_df):
    """
    Realistic clinic flow simulation by doctor-day:
    - different day windows by weekday / Saturday
    - uses realized patient arrival times
    - uses severity-based service times
    - counts intra-day idle + end-of-day idle
    - counts overtime if service extends beyond day end
    """
    df = schedule_df.copy()

    if "is_overbooked_extra" not in df.columns:
        df["is_overbooked_extra"] = 0
    if "source" not in df.columns:
        df["source"] = "scheduled"

    if "scheduled_minute" not in df.columns:
        df["scheduled_minute"] = df["time_slot"].apply(time_to_min)

    if "arrival_shift_min" not in df.columns:
        df["arrival_shift_min"] = 0.0

    if "arrival_minute" not in df.columns:
        df["arrival_minute"] = df["scheduled_minute"] + df["arrival_shift_min"]

    if "service_time_realized" not in df.columns or df["service_time_realized"].isna().all():
        df["service_time_realized"] = df.apply(sample_service_time_from_row, axis=1)
    else:
        missing_mask = df["service_time_realized"].isna()
        if missing_mask.any():
            df.loc[missing_mask, "service_time_realized"] = df.loc[missing_mask].apply(sample_service_time_from_row, axis=1)

    total_wait = 0.0
    total_idle = 0.0
    total_overtime = 0.0
    attended = 0
    no_show_realized = 0
    total_service_time = 0.0
    total_session_capacity = 0.0
    walk_in_attended = 0
    overbooked_extra_scheduled = 0

    for (doctor_id, appt_date), group in df.groupby(["doctor_id", "appointment_date"]):
        g = group.sort_values(["scheduled_minute", "is_overbooked_extra"]).copy()

        day_start, day_end = get_day_window(appt_date)
        current_time = day_start
        total_session_capacity += (day_end - day_start)

        for _, row in g.iterrows():
            show = int(row["show"])
            arrival_time = float(row["arrival_minute"])
            service_time = float(row["service_time_realized"])

            if int(row.get("is_overbooked_extra", 0)) == 1:
                overbooked_extra_scheduled += 1

            if show == 0:
                no_show_realized += 1
                continue

            attended += 1

            if str(row.get("source", "")).strip().lower() == "walk_in":
                walk_in_attended += 1

            # doctor idle until patient actually arrives
            if arrival_time > current_time:
                total_idle += (arrival_time - current_time)
                service_start = arrival_time
                patient_wait = 0.0
            else:
                service_start = current_time
                patient_wait = current_time - arrival_time

            total_wait += patient_wait
            current_time = service_start + service_time
            total_service_time += service_time

        # end-of-day idle or overtime
        if current_time < day_end:
            total_idle += (day_end - current_time)
        else:
            total_overtime += (current_time - day_end)

    avg_wait = total_wait / attended if attended > 0 else 0.0
    utilization = total_service_time / total_session_capacity if total_session_capacity > 0 else 0.0

    return {
        "average_wait_min": round(avg_wait, 4),
        "doctor_utilization": round(utilization, 4),
        "overtime_min": round(total_overtime, 4),
        "idle_min": round(total_idle, 4),
        "attended_patients": int(attended),
        "no_show_realized": int(no_show_realized),
        "walk_in_attended": int(walk_in_attended),
        "overbooked_extra_scheduled": int(overbooked_extra_scheduled),
    }

# =========================
# Run simulation
# =========================
global_mean_prob = float(pred["pred_no_show_prob"].mean())
all_rows = []

for _, sc in scenarios.iterrows():
    scenario_id = sc["scenario_id"]
    scenario_name = sc["scenario_name"]

    for run_id in range(1, N_RUNS_PER_SCENARIO + 1):
        sim = pred.copy()

        # scenario-adjusted no-show risk
        sim["p_final"] = sim.apply(
            lambda r: apply_scenario_adjustment(r, sc, global_mean_prob),
            axis=1
        )

        # realized attendance
        sim["show"] = np.random.binomial(1, 1 - sim["p_final"])

        # realized arrival and severity-based service times
        sim["scheduled_minute"] = sim["time_slot"].apply(time_to_min)
        sim["arrival_shift_min"] = sim.apply(lambda r: sample_arrival_shift(r, sc), axis=1)
        sim["arrival_minute"] = sim["scheduled_minute"] + sim["arrival_shift_min"]
        sim["service_time_realized"] = sim.apply(sample_service_time_from_row, axis=1)
        sim["source"] = "scheduled"

        # Generate same walk-ins for both policies in this run
        walkins_df = generate_walkins(sim, sc, doctors)

        # baseline
        baseline_df = assign_baseline_policy(sim)
        if not walkins_df.empty:
            baseline_df = pd.concat([baseline_df, walkins_df], ignore_index=True)

        baseline_metrics = simulate_policy(baseline_df)
        baseline_metrics.update(
            {
                "scenario_id": scenario_id,
                "scenario_name": scenario_name,
                "policy": "baseline",
                "run_id": run_id,
            }
        )
        all_rows.append(baseline_metrics)

        # optimized
        optimized_df = assign_optimized_policy(sim, sc)
        if not walkins_df.empty:
            optimized_df = pd.concat([optimized_df, walkins_df], ignore_index=True)

        optimized_metrics = simulate_policy(optimized_df)
        optimized_metrics.update(
            {
                "scenario_id": scenario_id,
                "scenario_name": scenario_name,
                "policy": "optimized",
                "run_id": run_id,
            }
        )
        all_rows.append(optimized_metrics)

results = pd.DataFrame(all_rows)
results.to_csv(OUTPUTS / "simulation_results.csv", index=False)

summary = (
    results.groupby(["scenario_id", "scenario_name", "policy"], as_index=False)
    .agg(
        {
            "average_wait_min": "mean",
            "doctor_utilization": "mean",
            "overtime_min": "mean",
            "idle_min": "mean",
            "attended_patients": "mean",
            "no_show_realized": "mean",
            "walk_in_attended": "mean",
            "overbooked_extra_scheduled": "mean",
        }
    )
)

summary = summary.sort_values(["scenario_id", "policy"])
summary.to_csv(OUTPUTS / "simulation_summary.csv", index=False)

# =========================
# Plots
# =========================
def save_pivot_bar(summary_df, value_col, title, filename, ylabel):
    pivot_df = summary_df.pivot(index="scenario_name", columns="policy", values=value_col)
    ax = pivot_df.plot(kind="bar", figsize=(10, 6))
    ax.set_title(title)
    ax.set_ylabel(ylabel)
    ax.set_xlabel("")
    plt.xticks(rotation=20)
    plt.tight_layout()
    plt.savefig(OUTPUTS / filename, dpi=300, bbox_inches="tight")
    plt.close()

save_pivot_bar(
    summary,
    "average_wait_min",
    "Average Waiting Time by Scenario and Policy",
    "simulation_waiting_time_comparison.png",
    "Minutes",
)

save_pivot_bar(
    summary,
    "doctor_utilization",
    "Doctor Utilization by Scenario and Policy",
    "simulation_utilization_comparison.png",
    "Utilization Rate",
)

save_pivot_bar(
    summary,
    "overtime_min",
    "Overtime by Scenario and Policy",
    "simulation_overtime_comparison.png",
    "Minutes",
)

save_pivot_bar(
    summary,
    "idle_min",
    "Idle Time by Scenario and Policy",
    "simulation_idle_time_comparison.png",
    "Minutes",
)

save_pivot_bar(
    summary,
    "walk_in_attended",
    "Walk-in Patients Attended by Scenario and Policy",
    "simulation_walkin_comparison.png",
    "Patients",
)

# main comparison figure
pivot_wait = summary.pivot(index="scenario_name", columns="policy", values="average_wait_min")
ax = pivot_wait.plot(kind="bar", figsize=(10, 6))
ax.set_title("Scheduling Policy Comparison")
ax.set_ylabel("Average Waiting Time (minutes)")
ax.set_xlabel("")
plt.xticks(rotation=20)
plt.tight_layout()
plt.savefig(OUTPUTS / "simulation_policy_comparison.png", dpi=300, bbox_inches="tight")
plt.close()

# =========================
# JSON meta summary
# =========================
simulation_summary_json = {
    "prediction_file": str(PRED_FILE),
    "scenario_file": str(SCENARIO_FILE),
    "doctor_file": str(DOCTOR_FILE),
    "slot_file_exists": bool(SLOT_FILE.exists()),
    "n_scenarios": int(scenarios.shape[0]),
    "runs_per_scenario": int(N_RUNS_PER_SCENARIO),
    "global_mean_pred_no_show_prob": round(global_mean_prob, 4),
    "clinic_hours": {
        "weekday": "09:00-15:00",
        "saturday": "09:00-12:00"
    },
    "simulation_features": {
        "end_of_day_idle_counted": True,
        "dynamic_overbook_threshold": True,
        "dynamic_max_extras_per_doctor_day": True,
        "arrival_variability": True,
        "severity_based_service_time": True,
        "saturday_shorter_shift": True,
        "walk_in_add_on_patients": True
    }
}

with open(OUTPUTS / "simulation_summary_meta.json", "w", encoding="utf-8") as f:
    json.dump(simulation_summary_json, f, indent=2)

# =========================
# Console output
# =========================
print("\nSimulation completed successfully.")
print(f"Scenarios: {scenarios.shape[0]}")
print(f"Runs per scenario: {N_RUNS_PER_SCENARIO}")
print("\nSaved files:")
print(f"- {OUTPUTS / 'simulation_results.csv'}")
print(f"- {OUTPUTS / 'simulation_summary.csv'}")
print(f"- {OUTPUTS / 'simulation_policy_comparison.png'}")
print(f"- {OUTPUTS / 'simulation_waiting_time_comparison.png'}")
print(f"- {OUTPUTS / 'simulation_utilization_comparison.png'}")
print(f"- {OUTPUTS / 'simulation_overtime_comparison.png'}")
print(f"- {OUTPUTS / 'simulation_idle_time_comparison.png'}")
print(f"- {OUTPUTS / 'simulation_walkin_comparison.png'}")
print(f"- {OUTPUTS / 'simulation_summary_meta.json'}")