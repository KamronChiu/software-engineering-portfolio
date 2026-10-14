package com.eduaccess.ui;

import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.Film;
import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.repository.FilmRepository;
import com.eduaccess.repository.ScreenRepository;
import com.eduaccess.service.SchedulingService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Route(value = "admin/schedule", layout = MainLayout.class)
@PageTitle("HCBS — Admin Schedule")
public class AdminScheduleView extends Div {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.UK);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final SchedulingService schedulingService;
    private final FilmRepository filmRepository;
    private final CinemaRepository cinemaRepository;
    private final ScreenRepository screenRepository;

    private final ComboBox<Cinema> cinemaBox = new ComboBox<>("Cinema");
    private final ComboBox<Screen> screenBox = new ComboBox<>("Screen");
    private final Div filmList = new Div();
    private final Div timetable = new Div();
    private final Span weekLabel = new Span();

    private LocalDate weekStart = startOfWeek(LocalDate.now());

    public AdminScheduleView(
            SchedulingService schedulingService,
            FilmRepository filmRepository,
            CinemaRepository cinemaRepository,
            ScreenRepository screenRepository
    ) {
        this.schedulingService = schedulingService;
        this.filmRepository = filmRepository;
        this.cinemaRepository = cinemaRepository;
        this.screenRepository = screenRepository;

        setWidthFull();
        getStyle()
                .set("min-height", "100vh")
                .set("background", "#020b1d")
                .set("color", "white")
                .set("padding", "44px 48px 90px 48px")
                .set("box-sizing", "border-box");

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1440px")
                .set("margin", "0 auto");

        H1 title = new H1("Admin Film Scheduler");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "42px")
                .set("font-weight", "950")
                .set("letter-spacing", "0.03em")
                .set("text-transform", "uppercase");

        Paragraph intro = new Paragraph("Drag a film from the left panel onto the weekly timetable. The service layer validates screen conflicts, four-shows-per-day limit, release dates and advance preview rules.");
        intro.getStyle()
                .set("color", "rgba(255,255,255,0.72)")
                .set("font-size", "16px")
                .set("line-height", "1.7")
                .set("max-width", "940px")
                .set("margin", "10px 0 30px 0");

        configureControls();
        renderFilmList();
        renderTimetable();

        Div app = new Div();
        app.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "300px 1fr")
                .set("gap", "28px")
                .set("align-items", "start");

        app.add(buildFilmPanel(), buildSchedulePanel());
        page.add(title, intro, app);
        add(page);
    }

    private void configureControls() {
        cinemaBox.setItems(cinemaRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Cinema::getCity).thenComparing(Cinema::getName))
                .toList());
        cinemaBox.setItemLabelGenerator(cinema -> cinema.getCity() + " — " + cinema.getName());
        cinemaBox.setPlaceholder("Select cinema");
        cinemaBox.setWidth("320px");
        styleDarkField(cinemaBox);

        screenBox.setItemLabelGenerator(this::formatScreenLabel);
        screenBox.setPlaceholder("Select screen");
        screenBox.setWidth("220px");
        styleDarkField(screenBox);

        cinemaBox.addValueChangeListener(event -> {
            Cinema cinema = event.getValue();
            screenBox.clear();
            if (cinema == null) {
                screenBox.setItems(List.of());
                renderTimetable();
                return;
            }

            List<Screen> screens = screenRepository.findByCinemaIdOrderByScreenNumberAsc(cinema.getId());
            screenBox.setItems(screens);
            if (!screens.isEmpty()) {
                screenBox.setValue(screens.get(0));
            }
            renderTimetable();
        });

        screenBox.addValueChangeListener(event -> renderTimetable());

        List<Cinema> cinemas = cinemaRepository.findAll();
        if (!cinemas.isEmpty()) {
            Cinema firstCinema = cinemas.stream()
                    .sorted(Comparator.comparing(Cinema::getCity).thenComparing(Cinema::getName))
                    .findFirst()
                    .orElse(cinemas.get(0));
            cinemaBox.setValue(firstCinema);
        }
    }

    private Div buildFilmPanel() {
        Div panel = darkCard();
        panel.getStyle()
                .set("position", "sticky")
                .set("top", "96px")
                .set("max-height", "calc(100vh - 130px)")
                .set("overflow", "auto");

        H2 heading = new H2("Films");
        heading.getStyle()
                .set("margin", "0")
                .set("font-size", "28px")
                .set("font-weight", "950");

        Paragraph hint = new Paragraph("Drag a film onto the grid to schedule it.");
        hint.getStyle()
                .set("color", "rgba(255,255,255,0.64)")
                .set("font-size", "15px")
                .set("line-height", "1.6")
                .set("margin", "8px 0 18px 0");

        filmList.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "14px");

        panel.add(heading, hint, filmList);
        return panel;
    }

    private Div buildSchedulePanel() {
        Div panel = darkCard();

        Div top = new Div();
        top.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "end")
                .set("gap", "20px")
                .set("flex-wrap", "wrap")
                .set("margin-bottom", "24px");

        Div filters = new Div();
        filters.getStyle()
                .set("display", "flex")
                .set("gap", "14px")
                .set("align-items", "end")
                .set("flex-wrap", "wrap");
        filters.add(cinemaBox, screenBox);

        Div nav = new Div();
        nav.getStyle()
                .set("display", "flex")
                .set("gap", "12px")
                .set("align-items", "center")
                .set("flex-wrap", "wrap");

        Button previous = secondaryButton("◀ Prev");
        previous.addClickListener(event -> {
            weekStart = weekStart.minusWeeks(1);
            renderTimetable();
        });

        weekLabel.getStyle()
                .set("font-size", "20px")
                .set("font-weight", "900")
                .set("min-width", "210px")
                .set("text-align", "center");

        Button next = secondaryButton("Next ▶");
        next.addClickListener(event -> {
            weekStart = weekStart.plusWeeks(1);
            renderTimetable();
        });

        Button today = secondaryButton("Today");
        today.addClickListener(event -> {
            weekStart = startOfWeek(LocalDate.now());
            renderTimetable();
        });

        Button addListing = primaryButton("+ Add Listing");
        addListing.addClickListener(event -> openCreateDialog(null, weekStart, LocalTime.of(12, 0)));

        nav.add(previous, weekLabel, next, today, addListing);
        top.add(filters, nav);

        timetable.setWidthFull();
        panel.add(top, timetable);
        return panel;
    }

    private void renderFilmList() {
        filmList.removeAll();

        filmRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Film::getTitle))
                .map(this::filmDragCard)
                .forEach(filmList::add);
    }

    private Component filmDragCard(Film film) {
        Div card = new Div();
        card.getElement().setAttribute("draggable", "true");
        card.getElement().executeJs("this.addEventListener('dragstart', function(e) { e.dataTransfer.setData('text/plain', $0); e.dataTransfer.effectAllowed = 'copy'; });", String.valueOf(film.getId()));

        card.getStyle()
                .set("background", filmCardColour(film))
                .set("border", "1px solid rgba(255,255,255,0.12)")
                .set("padding", "16px")
                .set("box-shadow", "0 16px 36px rgba(0,0,0,0.24)")
                .set("cursor", "grab")
                .set("user-select", "none");

        H3 title = new H3(film.getTitle());
        title.getStyle()
                .set("margin", "0 0 10px 0")
                .set("font-size", "19px")
                .set("font-weight", "950")
                .set("line-height", "1.1");

        Span meta = new Span(film.getAgeRating() + " · " + formatDuration(film.getDurationMinutes()) + " · " + film.getGenre());
        meta.getStyle()
                .set("font-size", "13px")
                .set("font-weight", "800")
                .set("color", "rgba(255,255,255,0.86)");

        card.add(title, meta);
        return card;
    }

    private void renderTimetable() {
        timetable.removeAll();

        LocalDate weekEnd = weekStart.plusDays(6);
        weekLabel.setText(weekStart.format(DateTimeFormatter.ofPattern("dd MMM", Locale.UK)) + " – " + weekEnd.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK)));

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "90px repeat(7, minmax(120px, 1fr))")
                .set("border", "1px solid rgba(255,255,255,0.14)")
                .set("background", "rgba(255,255,255,0.035)")
                .set("overflow", "hidden");

        grid.add(headerCell("Time"));
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            grid.add(headerCell(day.format(DAY_FORMAT)));
        }

        List<Screening> visibleScreenings = loadVisibleScreenings();

        for (int hour = 8; hour <= 22; hour++) {
            LocalTime slotTime = LocalTime.of(hour, 0);
            grid.add(timeCell(slotTime.format(TIME_FORMAT)));

            for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                LocalDate date = weekStart.plusDays(dayOffset);
                Div cell = dropCell(date, slotTime);

                int currentHour = hour;
                visibleScreenings.stream()
                        .filter(screening -> Objects.equals(screening.getScreeningDate(), date))
                        .filter(screening -> screening.getStartTime().getHour() == currentHour)
                        .sorted(Comparator.comparing(Screening::getStartTime))
                        .map(this::screeningCard)
                        .forEach(cell::add);

                grid.add(cell);
            }
        }

        timetable.add(grid);
    }

    private List<Screening> loadVisibleScreenings() {
        Screen screen = screenBox.getValue();
        Cinema cinema = cinemaBox.getValue();
        LocalDate weekEnd = weekStart.plusDays(6);

        return schedulingService.findAllScreeningsForAdmin()
                .stream()
                .filter(screening -> !screening.getScreeningDate().isBefore(weekStart))
                .filter(screening -> !screening.getScreeningDate().isAfter(weekEnd))
                .filter(screening -> cinema == null || Objects.equals(screening.getScreen().getCinema().getId(), cinema.getId()))
                .filter(screening -> screen == null || Objects.equals(screening.getScreen().getId(), screen.getId()))
                .sorted(Comparator.comparing(Screening::getScreeningDate).thenComparing(Screening::getStartTime))
                .toList();
    }

    private Div screeningCard(Screening screening) {
        Div card = new Div();
        card.getStyle()
                .set("background", filmCardColour(screening.getFilm()))
                .set("border", "1px solid rgba(255,255,255,0.16)")
                .set("box-shadow", "0 12px 30px rgba(0,0,0,0.28)")
                .set("padding", "10px")
                .set("margin-bottom", "8px")
                .set("position", "relative")
                .set("min-height", "74px");

        Span title = new Span(screening.getFilm().getTitle());
        title.getStyle()
                .set("display", "block")
                .set("font-size", "14px")
                .set("font-weight", "950")
                .set("line-height", "1.15")
                .set("padding-right", "42px");

        Span time = new Span(screening.getStartTime().format(TIME_FORMAT) + " - " + formatDuration(screening.getFilm().getDurationMinutes()));
        time.getStyle()
                .set("display", "block")
                .set("font-size", "13px")
                .set("font-weight", "800")
                .set("color", "rgba(255,255,255,0.86)")
                .set("margin-top", "5px");

        Span screen = new Span("Screen " + screening.getScreen().getScreenNumber() + " · " + formatType(screening.getScreeningType()));
        screen.getStyle()
                .set("display", "block")
                .set("font-size", "12px")
                .set("font-weight", "800")
                .set("color", "rgba(255,255,255,0.70)")
                .set("margin-top", "4px");

        Button edit = tinyButton("✎");
        edit.getStyle()
                .set("position", "absolute")
                .set("right", "8px")
                .set("bottom", "8px");
        edit.addClickListener(event -> openEditDialog(screening));

        Button delete = tinyButton("×");
        delete.getStyle()
                .set("position", "absolute")
                .set("right", "8px")
                .set("top", "8px");
        delete.addClickListener(event -> deleteScreening(screening));

        card.add(title, time, screen, edit, delete);
        return card;
    }

    private Div dropCell(LocalDate date, LocalTime time) {
        Div cell = new Div();
        cell.getStyle()
                .set("min-height", "108px")
                .set("border-left", "1px solid rgba(255,255,255,0.08)")
                .set("border-top", "1px solid rgba(255,255,255,0.08)")
                .set("padding", "8px")
                .set("box-sizing", "border-box")
                .set("transition", "background 0.18s ease, border-color 0.18s ease");

        cell.getElement().executeJs("this.addEventListener('dragover', function(e) { e.preventDefault(); this.style.background='rgba(56,189,248,0.10)'; }); this.addEventListener('dragleave', function(e) { this.style.background='transparent'; }); this.addEventListener('drop', function(e) { e.preventDefault(); this.style.background='transparent'; });");
        cell.getElement().addEventListener("drop", event -> {
            String filmIdText = event.getEventData().getString("event.dataTransfer.getData('text/plain')");
            Long filmId = parseLong(filmIdText);
            if (filmId == null) {
                Notification.show("Film could not be identified from the drag action.");
                return;
            }
            openCreateDialog(filmId, date, time);
        }).addEventData("event.dataTransfer.getData('text/plain')");

        return cell;
    }

    private Div headerCell(String text) {
        Div cell = new Div();
        cell.setText(text);
        cell.getStyle()
                .set("background", "rgba(255,255,255,0.08)")
                .set("border-left", "1px solid rgba(255,255,255,0.08)")
                .set("padding", "14px 10px")
                .set("font-size", "13px")
                .set("font-weight", "950")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.08em")
                .set("color", "rgba(255,255,255,0.86)");
        return cell;
    }

    private Div timeCell(String text) {
        Div cell = new Div();
        cell.setText(text);
        cell.getStyle()
                .set("border-top", "1px solid rgba(255,255,255,0.08)")
                .set("padding", "12px 10px")
                .set("font-size", "13px")
                .set("font-weight", "900")
                .set("color", "rgba(255,255,255,0.70)")
                .set("box-sizing", "border-box");
        return cell;
    }

    private void openCreateDialog(Long filmId, LocalDate date, LocalTime time) {
        Dialog dialog = screeningDialog("Add listing", null, filmId, date, time);
        dialog.open();
    }

    private void openEditDialog(Screening screening) {
        Dialog dialog = screeningDialog("Edit listing", screening, screening.getFilm().getId(), screening.getScreeningDate(), screening.getStartTime());
        dialog.open();
    }

    private Dialog screeningDialog(String titleText, Screening existing, Long filmId, LocalDate date, LocalTime time) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);

        Div content = new Div();
        content.getStyle()
                .set("width", "520px")
                .set("max-width", "calc(100vw - 64px)")
                .set("padding", "8px")
                .set("box-sizing", "border-box");

        H2 title = new H2(titleText);
        title.getStyle()
                .set("margin", "0 0 18px 0")
                .set("font-size", "28px")
                .set("font-weight", "950");

        ComboBox<Film> filmField = new ComboBox<>("Film");
        filmField.setItems(filmRepository.findAll().stream().sorted(Comparator.comparing(Film::getTitle)).toList());
        filmField.setItemLabelGenerator(Film::getTitle);
        filmField.setWidthFull();
        styleDarkField(filmField);

        ComboBox<Cinema> cinemaField = new ComboBox<>("Cinema");
        cinemaField.setItems(cinemaRepository.findAll().stream().sorted(Comparator.comparing(Cinema::getCity).thenComparing(Cinema::getName)).toList());
        cinemaField.setItemLabelGenerator(cinema -> cinema.getCity() + " — " + cinema.getName());
        cinemaField.setWidthFull();
        styleDarkField(cinemaField);

        ComboBox<Screen> screenField = new ComboBox<>("Screen");
        screenField.setItemLabelGenerator(this::formatScreenLabel);
        screenField.setWidthFull();
        styleDarkField(screenField);

        DatePicker dateField = new DatePicker("Date");
        dateField.setWidthFull();
        dateField.setValue(date == null ? LocalDate.now().plusDays(1) : date);
        styleDarkField(dateField);

        TimePicker timeField = new TimePicker("Start time");
        timeField.setStep(Duration.ofMinutes(10));
        timeField.setWidthFull();
        timeField.setValue(time == null ? LocalTime.of(12, 0) : time);
        styleDarkField(timeField);

        ComboBox<ScreeningType> typeField = new ComboBox<>("Screening type");
        typeField.setItems(ScreeningType.values());
        typeField.setItemLabelGenerator(this::formatType);
        typeField.setWidthFull();
        typeField.setValue(existing == null ? ScreeningType.REGULAR : existing.getScreeningType());
        styleDarkField(typeField);

        cinemaField.addValueChangeListener(event -> {
            Cinema selectedCinema = event.getValue();
            screenField.clear();
            if (selectedCinema == null) {
                screenField.setItems(List.of());
                return;
            }
            List<Screen> screens = screenRepository.findByCinemaIdOrderByScreenNumberAsc(selectedCinema.getId());
            screenField.setItems(screens);
            if (!screens.isEmpty()) {
                screenField.setValue(screens.get(0));
            }
        });

        if (filmId != null) {
            filmRepository.findById(filmId).ifPresent(filmField::setValue);
        }
        if (existing != null) {
            filmField.setValue(existing.getFilm());
            cinemaField.setValue(existing.getScreen().getCinema());
            setScreenById(screenField, existing.getScreen().getCinema().getId(), existing.getScreen().getId());
        } else {
            cinemaField.setValue(cinemaBox.getValue());
            Screen selectedScreen = screenBox.getValue();
            if (selectedScreen != null) {
                setScreenById(screenField, selectedScreen.getCinema().getId(), selectedScreen.getId());
            }
        }

        Button save = primaryButton(existing == null ? "Save listing" : "Update listing");
        save.addClickListener(event -> {
            try {
                validateDialog(filmField, screenField, dateField, timeField, typeField);

                if (existing == null) {
                    schedulingService.createScreening(
                            filmField.getValue().getId(),
                            screenField.getValue().getId(),
                            dateField.getValue(),
                            timeField.getValue(),
                            typeField.getValue()
                    );
                    Notification.show("Screening added successfully.");
                } else {
                    schedulingService.updateScreening(
                            existing.getId(),
                            filmField.getValue().getId(),
                            screenField.getValue().getId(),
                            dateField.getValue(),
                            timeField.getValue(),
                            typeField.getValue()
                    );
                    Notification.show("Screening updated successfully.");
                }

                dialog.close();
                renderTimetable();
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        });

        Button cancel = secondaryButton("Cancel");
        cancel.addClickListener(event -> dialog.close());

        HorizontalLayout buttons = new HorizontalLayout(save, cancel);
        buttons.getStyle().set("margin-top", "20px");

        content.add(title, filmField, cinemaField, screenField, dateField, timeField, typeField, buttons);
        dialog.add(content);
        return dialog;
    }


    private void setScreenById(ComboBox<Screen> screenField, Long cinemaId, Long screenId) {
        if (cinemaId == null || screenId == null) {
            return;
        }

        List<Screen> screens = screenRepository.findByCinemaIdOrderByScreenNumberAsc(cinemaId);
        screenField.setItems(screens);
        screens.stream()
                .filter(screen -> Objects.equals(screen.getId(), screenId))
                .findFirst()
                .ifPresent(screenField::setValue);
    }

    private void validateDialog(
            ComboBox<Film> filmField,
            ComboBox<Screen> screenField,
            DatePicker dateField,
            TimePicker timeField,
            ComboBox<ScreeningType> typeField
    ) {
        if (filmField.getValue() == null) {
            throw new IllegalArgumentException("Please select a film.");
        }
        if (screenField.getValue() == null) {
            throw new IllegalArgumentException("Please select a screen.");
        }
        if (dateField.getValue() == null) {
            throw new IllegalArgumentException("Please select a date.");
        }
        if (timeField.getValue() == null) {
            throw new IllegalArgumentException("Please select a start time.");
        }
        if (typeField.getValue() == null) {
            throw new IllegalArgumentException("Please select a screening type.");
        }
    }

    private void deleteScreening(Screening screening) {
        try {
            schedulingService.deleteScreening(screening.getId());
            Notification.show("Screening deleted successfully.");
            renderTimetable();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private static LocalDate startOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatScreenLabel(Screen screen) {
        if (screen == null) {
            return "";
        }
        return "Screen " + screen.getScreenNumber() + " — " + screen.getCapacity() + " seats";
    }

    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours == 0) {
            return mins + "m";
        }
        return mins == 0 ? hours + "h" : hours + "h " + mins + "m";
    }

    private String formatType(ScreeningType type) {
        return type == ScreeningType.ADVANCE_PREVIEW ? "Advance Preview" : "Regular";
    }

    private String filmCardColour(Film film) {
        if (film == null || film.getTitle() == null) {
            return "#0072ce";
        }
        int index = Math.abs(film.getTitle().hashCode()) % 5;
        return switch (index) {
            case 0 -> "#0072ce";
            case 1 -> "#be123c";
            case 2 -> "#047857";
            case 3 -> "#7c3aed";
            default -> "#c2410c";
        };
    }

    private Div darkCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "rgba(255,255,255,0.055)")
                .set("border", "1px solid rgba(255,255,255,0.13)")
                .set("box-shadow", "0 22px 70px rgba(0,0,0,0.32)")
                .set("padding", "24px")
                .set("box-sizing", "border-box");
        return card;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.getStyle()
                .set("height", "46px")
                .set("background", "#0072ce")
                .set("border-radius", "0")
                .set("font-weight", "900")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)")
                .set("padding", "0 34px 0 28px");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyle()
                .set("height", "42px")
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid rgba(255,255,255,0.42)")
                .set("border-radius", "0")
                .set("font-weight", "800");
        return button;
    }

    private Button tinyButton(String text) {
        Button button = new Button(text);
        button.getStyle()
                .set("width", "24px")
                .set("height", "24px")
                .set("min-width", "24px")
                .set("padding", "0")
                .set("border-radius", "0")
                .set("background", "rgba(0,0,0,0.30)")
                .set("color", "white")
                .set("border", "1px solid rgba(255,255,255,0.16)")
                .set("font-weight", "900");
        return button;
    }

    private void styleDarkField(Component component) {
        component.getElement().getStyle()
                .set("--vaadin-input-field-background", "rgba(255,255,255,0.08)")
                .set("--vaadin-input-field-value-color", "white")
                .set("--vaadin-input-field-label-color", "rgba(255,255,255,0.82)")
                .set("--vaadin-input-field-placeholder-color", "rgba(255,255,255,0.50)")
                .set("--vaadin-input-field-border-color", "rgba(255,255,255,0.20)")
                .set("--vaadin-input-field-focused-highlight", "#38bdf8")
                .set("color", "white");
    }
}
