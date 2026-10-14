README

Run commands:
python ActB_23084351.py
python ActC_23084351.py

Required CSV files in the same folder:
Coursework Assignment Table A.csv
Coursework Assignment Table B.csv
Coursework Assignment Table C.csv

Activity B method:
1. Table A is stored as a weighted adjacency-list graph.
2. Dijkstra is run from every node to pre-compute shortest travel times.
3. Each delivery row is treated as one task.
4. If one truck can complete all deliveries in an area-shift within 180 minutes, only one truck is used. Using a second truck when one truck is sufficient wastes fuel and personnel time.
5. If one truck is not feasible, every two-truck split is enumerated and the fastest feasible split is selected.
6. If a truck receives multiple deliveries in the same sector, it visits that sector once and counts 10 minutes unloading time per delivery.

Activity C method:
1. Only Table C is used for Activity C.
2. Each truck has a home area. It first completes a Table C home-area route and returns to headquarters.
3. If it has remaining time, it may complete one or more deliveries in other regions. This provides the required cross-regional truck dispatch function.
4. Exact-cover search assigns every delivery row exactly once, and binary search finds the smallest feasible shift completion time.
5. Repeated deliveries in the same sector are separate assignable tasks, but if they are assigned to the same truck, the route visits that sector once and unloading time is counted per delivery.

Validation with Greedy Baseline：
To validate the optimisation approach, I also compared the exact enumeration method with a greedy function. The greedy method makes local decisions, such as assigning the next delivery to the currently best available truck or visiting the nearest next sector. This is faster and simpler, but it may miss the global optimum because it does not test all possible task allocations and visiting orders. Therefore, greedy was used only as a comparison method, not as the final optimiser. The submitted solution uses Dijkstra’s shortest-path distances together with exact enumeration to search all feasible delivery-to-truck assignments and route orders. For Activity B, the selected solution is based on total driver time and resource efficiency. For Activity C, the selected solution is based on minimum feasible shift completion time with cross-regional support. The greedy comparison helps show why exact enumeration is more reliable: greedy can produce a reasonable route quickly, but the exact method gives a stronger guarantee because every feasible allocation and route order is tested within the small problem size.

Output files:
ActivityB_Routes_Output.csv
ActivityB_Optimisation_Audit.csv
ActivityC_Routes_Output.csv
ActivityC_Shift_Summary.csv
ActivityC_Optimisation_Audit.csv
