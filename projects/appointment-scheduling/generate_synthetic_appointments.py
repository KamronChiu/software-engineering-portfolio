
import pandas as pd
import numpy as np
from scipy.special import expit

np.random.seed(42)

patients = pd.read_csv("./data/core/patients_clean.csv")
doctors = pd.read_csv("./data/core/doctors_clean.csv")

dates = pd.bdate_range("2023-09-01", "2024-02-29", freq="C")

# date-level weather
weather_by_date = {}
for d in dates:
    if d.month in [12, 1, 2]:
        weather_by_date[d] = np.random.choice(["sunny", "cloudy", "rainy"], p=[0.40, 0.25, 0.35])
    else:
        weather_by_date[d] = np.random.choice(["sunny", "cloudy", "rainy"], p=[0.55, 0.25, 0.20])

departments = doctors["department"].unique().tolist()
doc_map = {dept: doctors[doctors["department"] == dept]["doctor_id"].tolist() for dept in departments}

def sample_department(p):
    weights = {"cardiology": 1.0, "dermatology": 1.0, "orthopedics": 1.0, "pediatrics": 1.0}
    if p["has_chronic_disease"] == 1:
        weights["cardiology"] += 1.5
        weights["orthopedics"] += 0.4
    if p["age_group"] == "child":
        weights["pediatrics"] += 4.0
    if p["age_group"] == "senior":
        weights["cardiology"] += 1.0
        weights["orthopedics"] += 1.0
    if p["age_group"] == "youth":
        weights["dermatology"] += 0.8
    probs = np.array([weights[d] for d in departments], dtype=float)
    probs = probs / probs.sum()
    return np.random.choice(departments, p=probs)

appt_type_by_dept = {
    "cardiology": (["checkup", "follow-up", "consultation"], [0.45, 0.40, 0.15]),
    "dermatology": (["checkup", "follow-up", "consultation", "procedure"], [0.35, 0.25, 0.25, 0.15]),
    "orthopedics": (["checkup", "follow-up", "consultation", "procedure"], [0.25, 0.30, 0.25, 0.20]),
    "pediatrics": (["checkup", "follow-up", "vaccination"], [0.50, 0.20, 0.30]),
}

def generate_appointments(n=2200):
    rows = []
    for i in range(1, n + 1):
        p = patients.sample(1, replace=True).iloc[0]
        dept = sample_department(p)
        doctor_id = np.random.choice(doc_map[dept])
        drow = doctors[doctors["doctor_id"] == doctor_id].iloc[0]

        lead_time = int(np.clip(np.random.gamma(shape=2.2, scale=4.5), 0, 35))
        booking_date = pd.Timestamp(np.random.choice(dates[:-35]))
        appointment_date = booking_date + pd.Timedelta(days=max(1, lead_time))
        while appointment_date.weekday() >= 5:
            appointment_date += pd.Timedelta(days=1)
        if appointment_date > dates[-1]:
            appointment_date = pd.Timestamp(np.random.choice(dates))

        lead_time_days = int((appointment_date - booking_date).days)
        weather = weather_by_date.get(pd.Timestamp(appointment_date), "sunny")
        time_slot = np.random.choice(["morning", "afternoon"], p=[0.58, 0.42])

        appt_types, probs = appt_type_by_dept[dept]
        appointment_type = np.random.choice(appt_types, p=probs)
        is_follow_up = int(appointment_type == "follow-up" or (p["previous_visits"] >= 3 and np.random.rand() < 0.25))

        reminder_prob = 0.72
        if lead_time_days <= 2:
            reminder_prob -= 0.12
        if appointment_type in ["follow-up", "consultation"]:
            reminder_prob += 0.05
        reminder_sent = int(np.random.rand() < np.clip(reminder_prob, 0.45, 0.92))

        prev_ns_rate = 1 - float(p["previous_attendance_rate"])
        first_visit = int(p["previous_visits"] == 0)

        # interpretable no-show generation rule
        logit = -2.25
        logit += 2.2 * prev_ns_rate
        logit += 0.03 * min(lead_time_days, 30)
        logit += 0.22 if time_slot == "afternoon" else 0.0
        logit += 0.28 if appointment_date.day_name().lower() == "monday" else 0.0
        logit += 0.12 if appointment_date.day_name().lower() == "friday" else 0.0
        logit += 0.35 if weather == "rainy" else 0.10 if weather == "cloudy" else 0.0
        logit += 0.32 if p["distance_band"] == "far" else 0.12 if p["distance_band"] == "medium" else 0.0
        logit += 0.12 if appointment_type == "checkup" else 0.0
        logit += 0.10 if first_visit else 0.0
        logit += 0.08 if dept == "orthopedics" else 0.05 if dept == "pediatrics" else 0.0
        logit -= 0.10 if p["has_chronic_disease"] == 1 else 0.0
        logit -= 0.10 if is_follow_up else 0.0
        logit -= 0.48 if reminder_sent else 0.0
        logit -= 0.05 if drow["doctor_level"] == "senior" else 0.0

        no_show_prob = float(expit(logit))
        no_show = int(np.random.rand() < no_show_prob)

        rows.append({
            "appointment_id": f"a{i:05d}",
            "patient_id": p["patient_id"],
            "doctor_id": doctor_id,
            "department": dept,
            "booking_date": booking_date.date().isoformat(),
            "appointment_date": appointment_date.date().isoformat(),
            "lead_time_days": lead_time_days,
            "day_of_week": appointment_date.day_name().lower(),
            "time_slot": time_slot,
            "appointment_type": appointment_type,
            "is_follow_up": is_follow_up,
            "reminder_sent": reminder_sent,
            "weather_type": weather,
            "no_show_prob_rule": round(no_show_prob, 4),
            "no_show": no_show
        })
    return pd.DataFrame(rows)

appointments = generate_appointments(2200)

model_base = (
    appointments
    .merge(patients, on="patient_id", how="left")
    .merge(doctors, on="doctor_id", how="left", suffixes=("", "_doctor"))
)

model_base["historical_no_show_rate"] = 1 - model_base["previous_attendance_rate"]
model_base["appointment_month"] = pd.to_datetime(model_base["appointment_date"]).dt.month
model_base["booking_weekday"] = pd.to_datetime(model_base["booking_date"]).dt.day_name().str.lower()
model_base["is_long_lead"] = (model_base["lead_time_days"] >= 14).astype(int)
model_base["is_rainy"] = (model_base["weather_type"] == "rainy").astype(int)
model_base["is_monday"] = (model_base["day_of_week"] == "monday").astype(int)
model_base["is_afternoon"] = (model_base["time_slot"] == "afternoon").astype(int)

appointments.to_csv("./data/core/appointments_synthetic_expanded.csv", index=False)
model_base.to_csv("./data/core/model_base_synthetic_expanded.csv", index=False)

print("appointments:", len(appointments))
print("no-show rate:", round(appointments["no_show"].mean(), 4))
