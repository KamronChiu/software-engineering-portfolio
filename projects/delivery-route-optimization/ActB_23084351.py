"""
Activity B
Using a second truck is not necessary if one truck can complete all deliveries in a shift. This would waste fuel and time. First, the program checks if one local truck can finish all deliveries within 180 minutes. If it can, only that truck will be used. If one truck cannot do the job, the program will look at all the ways to split the deliveries between the two local trucks and choose the fastest schedule that uses both trucks.

Algorithm steps:
1. Store Table A as a weighted adjacency-list graph.
2. Run Dijkstra from every node to get shortest travel times.
3. Treat each delivery row as one task.
4. For a truck route, merge same-sector tasks only inside that truck. For example: A1, A1 becomes A1 x2, visited once, with 20 mins unloading.
5. Optimise by using the fewest trucks first, then the shortest time.
"""

import csv, heapq, itertools
from collections import Counter, defaultdict
from functools import lru_cache

HQ = "HQ"
AREAS = "ABCD"
SHIFT_LIMIT = 180
UNLOAD = 10
TABLE_A = "Coursework Assignment Table A.csv"
TABLE_B = "Coursework Assignment Table B.csv"


def add_edge(g, a, b, w):
    g[a].append((b, w)); g[b].append((a, w))


def load_graph(path=TABLE_A):
    """Read Table A into an undirected weighted graph."""
    g = defaultdict(list)
    with open(path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            area = row["Delivery Area"].strip()
            hq = row["Time from Headquarters (mins)"].strip()
            if area and hq:
                add_edge(g, HQ, area + "1", int(hq))
            s1, s2 = row["Sector 1"].strip(), row["Sector 2"].strip()
            w = row["Time between sectors (mins)"].strip()
            if s1 and s2 and w:
                add_edge(g, s1, s2, int(w))
    return dict(g)


def load_deliveries(path):
    """Read Table B. Empty Area cells inherit the previous area."""
    data = defaultdict(list)
    current_area = None
    with open(path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            if row["Area"].strip():
                current_area = row["Area"].strip()
            if row["Shift"].strip() and row["Sector"].strip():
                data[(current_area, int(row["Shift"]))].append(row["Sector"].strip())
    return data


def dijkstra(g, source):
    """Dijkstra shortest paths for non-negative travel times."""
    dist = {v: float("inf") for v in g}
    dist[source] = 0
    pq = [(0, source)]
    while pq:
        d, v = heapq.heappop(pq)
        if d != dist[v]:
            continue
        for u, w in g[v]:
            nd = d + w
            if nd < dist[u]:
                dist[u] = nd
                heapq.heappush(pq, (nd, u))
    return dist


def all_pairs(g):
    return {v: dijkstra(g, v) for v in g}


@lru_cache(None)
def route_for_counts(count_items):
    """Best HQ -> unique sectors -> HQ route for one truck."""
    counts = dict(count_items)
    if not counts:
        return (0, 0, 0, "HQ -> HQ", "No deliveries")

    sectors = tuple(sorted(counts))
    unload = sum(counts.values()) * UNLOAD
    best_travel, best_order = 10**9, None

    # Only six sectors exist in an area, so exact permutation is small and clear.
    for order in itertools.permutations(sectors):
        stops = (HQ,) + order + (HQ,)
        travel = sum(DIST[a][b] for a, b in zip(stops, stops[1:]))
        if travel < best_travel:
            best_travel, best_order = travel, order

    label = ", ".join(f"{s} x{counts[s]}" for s in sorted(counts))
    route = "HQ -> " + " -> ".join(best_order) + " -> HQ"
    return (best_travel + unload, best_travel, unload, route, label)


def route_for_tasks(tasks):
    """Tasks are individual deliveries, and same-sector tasks merge inside a truck."""
    return route_for_counts(tuple(sorted(Counter(tasks).items())))


def solve_area_shift(sectors):
    """Use one truck if feasible; otherwise exact split across two trucks."""
    all_route = route_for_tasks(tuple(sectors))
    empty = route_for_tasks(())

    # haven't use the second truck if one truck can do the work.
    if all_route[0] <= SHIFT_LIMIT:
        return {
            "trucks_used": 1,
            "completion": all_route[0],
            "routes": [all_route, empty],
            "splits_checked": 1,
            "reason": "One truck feasible, so second truck not used to save resources",
        }

    # If one truck cannot finish within 180 minutes, try every two-truck split.
    n = len(sectors)
    best = None
    for mask in range(1 << n):
        left = tuple(sectors[i] for i in range(n) if mask & (1 << i))
        right = tuple(sectors[i] for i in range(n) if not mask & (1 << i))
        r1, r2 = route_for_tasks(left), route_for_tasks(right)
        if r1[0] > SHIFT_LIMIT or r2[0] > SHIFT_LIMIT:
            continue
        candidate = (max(r1[0], r2[0]), r1[0] + r2[0], r1, r2)
        if best is None or candidate[:2] < best[:2]:
            best = candidate

    if best is None:
        raise ValueError("No feasible one or two-truck schedule found.")

    return {
        "trucks_used": 2,
        "completion": best[0],
        "routes": [best[2], best[3]],
        "splits_checked": 2 ** n,
        "reason": "One truck infeasible. Exact two-truck split used",
    }


def main():
    deliveries = load_deliveries(TABLE_B)
    rows, audit = [], []
    print("Activity B - resource-aware exact schedules")
    print("=" * 58)

    for area in AREAS:
        for shift in range(1, 5):
            sectors = deliveries.get((area, shift), [])
            result = solve_area_shift(sectors)
            print(f"Area {area}, Shift {shift}: {result['completion']} mins, trucks used = {result['trucks_used']}")

            for truck_no, r in [(1, result["routes"][0]), (2, result["routes"][1])]:
                total, travel, unload, route, label = r
                used = "Yes" if total > 0 else "No"
                print(f"  {area}-Truck-{truck_no}: {label} | total={total} | used={used}")
                rows.append({
                    "Area": area, "Shift": shift, "Truck": f"{area}-Truck-{truck_no}",
                    "Used": used, "Deliveries": label, "Route": route,
                    "Travel Time": travel, "Unloading Time": unload,
                    "Total Truck Time": total, "Spare Capacity": SHIFT_LIMIT - total,
                    "Shift Completion Time": result["completion"],
                    "Trucks Used In This Area-Shift": result["trucks_used"],
                    "Feasible Within 180 Mins": "Yes" if total <= SHIFT_LIMIT else "No",
                })
            audit.append({
                "Area": area, "Shift": shift,
                "Input Delivery Rows": len(sectors),
                "Trucks Used": result["trucks_used"],
                "Completion Time": result["completion"],
                "Splits Checked": result["splits_checked"],
                "All Trucks Within 180 Mins": "Yes" if max(r[0] for r in result["routes"]) <= SHIFT_LIMIT else "No",
                "Resource Rule Applied": result["reason"],
                "Policy": "fewest trucks first; individual delivery tasks; same-sector tasks merge inside each truck",
            })
            print()

    with open("ActivityB_Routes_Output.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
    with open("ActivityB_Optimisation_Audit.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(audit[0])); writer.writeheader(); writer.writerows(audit)
    print("Written: ActivityB_Routes_Output.csv, ActivityB_Optimisation_Audit.csv")


if __name__ == "__main__":
    GRAPH = load_graph()
    DIST = all_pairs(GRAPH)
    main()
