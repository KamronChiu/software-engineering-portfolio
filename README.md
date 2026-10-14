# Kamron Zhao — Software Engineering Portfolio

A private preparation workspace for software development, web applications, algorithms, and data/AI projects. This repository combines source snapshots for review. Selected projects can be split into dedicated public repositories after their documentation, attribution, and reproducibility have been checked.

## Featured projects

| Project | Focus | Current evidence and limits |
|---|---|---|
| [StudentBay](projects/studentbay) | Java, Spring Boot, JPA, MySQL, web workflows | Marketplace source, integration tests, design docs, and a personal contribution document. Originally a group project; authorship must remain attributed. |
| [Cinema booking](projects/cinema-booking) | Java, Spring Boot, Vaadin, H2 | Booking, scheduling, cancellation/refund policies, reporting, and test source. Existing public repository: [MovieSystem](https://github.com/KamronChiu/MovieSystem). |
| [Web test automation](projects/web-test-automation) | Selenium, JUnit, page objects | Login, product, cart, and checkout test source. Browser tests have not been run in this preparation pass. |
| [Delivery route optimization](projects/delivery-route-optimization) | Python, graph algorithms, combinatorial search | Dijkstra distances, truck allocation, route enumeration, and cross-area dispatch on a small coursework instance. |
| [NeuMini](projects/neumini) | C, filesystems, processes | Educational mini-OS simulator using host processes. This is a group project, not a standalone operating-system kernel. |
| [Appointment scheduling](projects/appointment-scheduling) | Python, scikit-learn, simulation, Streamlit | Synthetic-data prototype. Existing model results are included with limitations; record-level data are withheld. |

## Supporting work

- [Library system](projects/library-system): Express, SQLite, server-rendered pages. Local database omitted; initialization and authentication need review.
- [Cinema web application](projects/cinema-web): Vue, Express, MySQL. Environment configuration omitted; database setup and authentication need review.
- [IDEA learning archive](archive/idea): ISD, ProjectDemo, ST1, WebTesting, app, basic_code, eduaccess-platform-mini, and webAppDev. Earlier prototypes and exercises are retained for completeness.

The eleven IntelliJ IDEA project directories are represented here: `StudentBay`, `eduaccess`, and `FinalProjects` are under `projects/`; the other eight are under `archive/idea/`.

## How to use this repository

Each project is independent. Start with its own README or build file. Do not run a build from the portfolio root. Read [PROJECT_STATUS.md](PROJECT_STATUS.md) for missing setup and [VALIDATION.md](VALIDATION.md) for checks actually performed.

## Provenance and presentation

This is a source snapshot, not a migration of the original Git histories. It includes coursework, group work, personal extensions, and learning exercises. Team projects must be described using the author's actual contribution; existing team acknowledgments are retained. The current StudentBay source is associated with a Gitee group repository. Do not represent all code in a group repository as individual work.

Local databases, uploads, environment secrets, IDE metadata, dependency directories, compiled artifacts, lecture slides, assignment briefs, and report documents are excluded. Some coursework input tables are retained privately for reproducibility; check their redistribution terms before publishing. Images bundled with projects also need provenance review before public release. No blanket open-source license has been added because ownership differs across projects.

## Next improvements

1. Confirm personal contributions and select a small set of projects for the public profile.
2. Add an English overview, screenshots, architecture, and reproducible setup to each selected project.
3. Run the Java/backend test suites and document actual results; fix identified setup and security issues.
4. Complete the data provenance, evaluation, and baseline comparisons for the data/AI project.
5. Split finished projects into dedicated repositories and link them from a profile README.
