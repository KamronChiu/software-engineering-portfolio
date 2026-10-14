"""
Activity C

Idea:
1. Read Table A as a weighted graph.
2. Use Dijkstra from every node to get shortest travel times.
3. Treat each delivery row in Table C as one assignable task.
4. For a truck, calculate a Table C home-area route first. If it still has time, it can support one or more deliveries in other areas after returning to HQ.
5. Use exact-cover search to assign every delivery task exactly once.
6. Use binary search to find the smallest feasible shift completion time.

rule:
A sector such as A1 may have more than one delivery. These deliveries can be split between trucks. If the same truck receives more than one delivery in the same sector, it visits that sector once and counts 10 minutes unloading per delivery.
"""

import csv, heapq, itertools
from collections import Counter, defaultdict
from functools import lru_cache

HQ = "HQ"
AREAS = "ABCD"
TRUCKS = [(a, i) for a in AREAS for i in (1, 2)]
SHIFT_LIMIT = 180
UNLOAD = 10
TABLE_A = "Coursework Assignment Table A.csv"
TABLE_C = "Coursework Assignment Table C.csv"


def add_edge(g, a, b, w):
    g[a].append((b, w)); g[b].append((a, w))


def load_graph(path=TABLE_A):
    g = defaultdict(list)
    with open(path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            area = row["Delivery Area"].strip()
            hq_time = row["Time from Headquarters (mins)"].strip()
            if area and hq_time:
                add_edge(g, HQ, area + "1", int(hq_time))
            s1, s2 = row["Sector 1"].strip(), row["Sector 2"].strip()
            w = row["Time between sectors (mins)"].strip()
            if s1 and s2 and w:
                add_edge(g, s1, s2, int(w))
    return dict(g)


def load_deliveries(path):
    data = defaultdict(list)
    area = None
    with open(path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            if row["Area"].strip():
                area = row["Area"].strip()
            if row["Shift"].strip() and row["Sector"].strip():
                data[int(row["Shift"])].append((area, row["Sector"].strip()))
    return data


def dijkstra(g, source):
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
    """Best HQ -> unique sectors -> HQ route for one segment."""
    counts = dict(count_items)
    if not counts:
        return (0, 0, 0, "HQ -> HQ", "No deliveries")
    sectors = tuple(sorted(counts))
    unload = sum(counts.values()) * UNLOAD
    best_travel, best_order = 10**9, None
    for order in itertools.permutations(sectors):
        stops = (HQ,) + order + (HQ,)
        travel = sum(DIST[a][b] for a, b in zip(stops, stops[1:]))
        if travel < best_travel:
            best_travel, best_order = travel, order
    route = "HQ -> " + " -> ".join(best_order) + " -> HQ"
    label = ", ".join(f"{s} x{counts[s]}" for s in sorted(counts))
    return (best_travel + unload, best_travel, unload, route, label)


def solve_shift(shift, tasks):
    """Return the minimum time schedule for one shift."""
    n = len(tasks)
    all_mask = (1 << n) - 1
    area_ids = {a: [i for i, (area, _s) in enumerate(tasks) if area == a] for a in AREAS}

    @lru_cache(None)
    def segment(area, mask):
        counts = Counter(tasks[i][1] for i in area_ids[area] if mask & (1 << i))
        return route_for_counts(tuple(sorted(counts.items())))

    # Pre-compute every possible subset route for each area.
    area_subsets = {}
    for area in AREAS:
        rows = []
        ids = area_ids[area]
        for bits in range(1 << len(ids)):
            mask = sum(1 << ids[j] for j in range(len(ids)) if bits & (1 << j))
            rows.append((mask, segment(area, mask)[0]))
        rows.sort(key=lambda x: (x[1], -x[0].bit_count()))
        area_subsets[area] = rows

    @lru_cache(None)
    def options(home, limit):
        """All task sets one truck can do within limit: home first, then support."""
        ans = []
        home_i = AREAS.index(home)
        away = [a for a in AREAS if a != home]
        for home_mask, home_cost in area_subsets[home]:
            if home_cost > limit:
                continue
            masks = [0, 0, 0, 0]
            masks[home_i] = home_mask
            ans.append((home_mask, home_cost, tuple(masks)))

            #support is allowed after the truck has completed a non-empty home-area segment in Table C.
            if home_mask == 0:
                continue

            def add_support(pos, used_mask, cost, masks):
                if cost > limit:
                    return
                if pos == len(away):
                    if used_mask != home_mask:
                        ans.append((used_mask, cost, tuple(masks)))
                    return
                area = away[pos]
                add_support(pos + 1, used_mask, cost, masks)
                for support_mask, support_cost in area_subsets[area]:
                    if support_mask == 0:
                        continue
                    new_cost = cost + support_cost
                    if new_cost <= limit:
                        k = AREAS.index(area)
                        old = masks[k]
                        masks[k] = support_mask
                        add_support(pos + 1, used_mask | support_mask, new_cost, masks)
                        masks[k] = old

            add_support(0, home_mask, home_cost, masks[:])

        # Remove duplicate task sets, keeping the cheapest version.
        best = {}
        for mask, cost, masks in ans:
            key = (mask, masks)
            if cost < best.get(key, 10**9):
                best[key] = cost
        out = [(mask, cost, masks) for (mask, masks), cost in best.items()]
        return tuple(sorted(out, key=lambda x: (-x[0].bit_count(), x[1])))

    def feasible(limit):
        truck_options = [options(home, limit) for home, _no in TRUCKS]
        candidates = [[] for _ in range(n)]
        for truck_i, opts in enumerate(truck_options):
            for opt_i, opt in enumerate(opts):
                mask = opt[0]
                m = mask
                while m:
                    bit = m & -m
                    task_i = bit.bit_length() - 1
                    candidates[task_i].append((truck_i, opt_i, opt))
                    m -= bit
        if any(not c for c in candidates):
            return None, truck_options

        @lru_cache(None)
        def dfs(done, used_trucks):
            if done == all_mask:
                return ()
            remaining = all_mask ^ done
            chosen = None
            m = remaining
            while m:
                bit = m & -m
                task_i = bit.bit_length() - 1
                m -= bit
                valid = [c for c in candidates[task_i]
                         if not (used_trucks & (1 << c[0])) and not (c[2][0] & done)]
                if not valid:
                    return None
                if chosen is None or len(valid) < len(chosen):
                    chosen = valid
            for truck_i, opt_i, opt in sorted(chosen, key=lambda x: (-x[2][0].bit_count(), x[2][1])):
                rest = dfs(done | opt[0], used_trucks | (1 << truck_i))
                if rest is not None:
                    return ((truck_i, opt_i),) + rest
            return None

        return dfs(0, 0), truck_options

    # Binary search the smallest feasible completion time.
    lo, hi, best = 0, SHIFT_LIMIT, None
    while lo <= hi:
        mid = (lo + hi) // 2
        solution, truck_options = feasible(mid)
        if solution is not None:
            best = (mid, solution, truck_options)
            hi = mid - 1
        else:
            lo = mid + 1
    limit, solution, truck_options = best

    selected = [(0, 0, (0, 0, 0, 0)) for _ in TRUCKS]
    for truck_i, opt_i in solution:
        selected[truck_i] = truck_options[truck_i][opt_i]
    below, _ = feasible(limit - 1) if limit > 0 else (None, None)
    return limit, selected, below is not None, segment


def segment_label(role, area, r):
    total, _travel, _unload, route, label = r
    return f"{role} Area {area}: {label} ({total} mins)", route


def main():
    data = load_deliveries(TABLE_C)
    route_rows, summary_rows, audit_rows = [], [], []
    print("Activity C - simple individual-delivery exact optimum")
    print("=" * 60)

    for shift in range(1, 5):
        tasks = data[shift]
        completion, selected, feasible_below, segment = solve_shift(shift, tasks)
        max_time = max(opt[1] for opt in selected)
        print(f"Shift {shift}: completion time = {completion} mins")
        summary_rows.append({"Shift": shift, "Completion Time": completion})
        covered = Counter()

        for (home, no), opt in zip(TRUCKS, selected):
            mask, total, masks = opt
            covered.update(i for i in range(len(tasks)) if mask & (1 << i))
            parts, routes = [], []
            # Display order follows the rule that home route first, then support routes.
            display_areas = [home] + [a for a in AREAS if a != home]
            for area in display_areas:
                area_mask = masks[AREAS.index(area)]
                if area_mask == 0:
                    continue
                role = "Home" if area == home else "Support"
                text, route = segment_label(role, area, segment(area, area_mask))
                parts.append(text); routes.append(route)
            route_text = " | ".join(parts) if parts else "No deliveries"
            print(f"  {home}-Truck-{no}: {route_text} | total={total}")
            route_rows.append({
                "Shift": shift, "Truck": f"{home}-Truck-{no}", "Home Area": home,
                "Segments": route_text,
                "Routes": " | ".join(routes) if routes else "HQ -> HQ",
                "Total Truck Time": total,
                "Spare Capacity": SHIFT_LIMIT - total,
                "Shift Completion Time": completion,
                "Cross-Area Support Used": "Yes" if any(masks[AREAS.index(a)] for a in AREAS if a != home) else "No",
                "Feasible Within 180 Mins": "Yes" if total <= SHIFT_LIMIT else "No",
            })
        print()

        audit_rows.append({
            "Shift": shift,
            "Input Delivery Rows": len(tasks),
            "Rows Covered Exactly Once": "Yes" if all(covered[i] == 1 for i in range(len(tasks))) else "No",
            "All Trucks Within 180 Mins": "Yes" if max_time <= SHIFT_LIMIT else "No",
            "Completion Time": completion,
            "Max Truck Time": max_time,
            "No Feasible Solution Below Final Time": "Yes" if not feasible_below else "No",
            "Policy": "individual deliveries; same-sector deliveries may split across trucks",
        })

    for filename, rows in [
        ("ActivityC_Routes_Output.csv", route_rows),
        ("ActivityC_Shift_Summary.csv", summary_rows),
        ("ActivityC_Optimisation_Audit.csv", audit_rows),
    ]:
        with open(filename, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=list(rows[0]))
            writer.writeheader(); writer.writerows(rows)
    print("Written: ActivityC_Routes_Output.csv, ActivityC_Shift_Summary.csv, ActivityC_Optimisation_Audit.csv")


if __name__ == "__main__":
    GRAPH = load_graph()
    DIST = all_pairs(GRAPH)
    main()
