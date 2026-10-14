import pandas as pd
import streamlit as st

st.set_page_config(page_title="Outpatient No-show Project Dashboard", layout="wide")

model_metrics = pd.read_csv("outputs/model_metrics.csv")
sim_summary = pd.read_csv("outputs/simulation_summary.csv")

st.title("Outpatient Appointment Optimization Dashboard")

st.subheader("1. Dataset Overview")
st.metric("Synthetic Appointments", "2200")
st.metric("No-show Rate", "19.64%")

st.subheader("2. Model Performance")
st.dataframe(model_metrics, use_container_width=True)
st.image("outputs/model_comparison.png", caption="Model Comparison")

st.subheader("3. Scheduling Simulation")
st.dataframe(sim_summary, use_container_width=True)
st.image("outputs/simulation_policy_comparison.png", caption="Simulation Policy Comparison")

st.subheader("4. Scenario Trends")
pivot_wait = sim_summary.pivot(index="scenario_name", columns="policy", values="average_wait_min")
st.line_chart(pivot_wait)

pivot_util = sim_summary.pivot(index="scenario_name", columns="policy", values="doctor_utilization")
st.line_chart(pivot_util)