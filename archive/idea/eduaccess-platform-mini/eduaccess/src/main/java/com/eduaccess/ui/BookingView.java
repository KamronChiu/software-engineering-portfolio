package com.eduaccess.ui;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import com.eduaccess.domain.Booking;
import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.Film;
import com.eduaccess.domain.Screening;
import com.eduaccess.domain.ScreeningType;
import com.eduaccess.domain.Seat;
import com.eduaccess.repository.CinemaRepository;
import com.eduaccess.service.BookingService;
import com.eduaccess.service.PricingService;
import com.eduaccess.service.ScreeningService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "booking", layout = MainLayout.class)
@PageTitle("HCBS — Booking")
public class BookingView extends Div implements HasUrlParameter<Long> {

    private final CinemaRepository cinemaRepository;
    private final ScreeningService screeningService;
    private final BookingService bookingService;
    private final PricingService pricingService;

    private final ComboBox<Film> filmBox = new ComboBox<>();
    private final ComboBox<Cinema> cinemaBox = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker();

    private final Div hero = new Div();
    private final Div showtimeArea = new Div();

    private final Dialog seatDialog = new Dialog();
    private final Div seatMap = new Div();
    private final Div selectedSeatChips = new Div();
    private final Span totalPriceLabel = new Span("£0.00");

    private final TextField customerNameField = new TextField("Customer name");
    private final EmailField customerEmailField = new EmailField("Customer email");
    private final TextArea receiptArea = new TextArea("Booking receipt");

    private List<Screening> currentScreenings = List.of();
    private Long selectedFilmId;
    private Long requestedFilmId;
    private Screening selectedScreening;

    private List<BookingService.SeatOption> currentSeatOptions = List.of();
    private final Set<Long> selectedSeatIds = new LinkedHashSet<>();
    private final Map<Long, Seat> seatById = new LinkedHashMap<>();

    public BookingView(
            CinemaRepository cinemaRepository,
            ScreeningService screeningService,
            BookingService bookingService,
            PricingService pricingService
    ) {
        this.cinemaRepository = cinemaRepository;
        this.screeningService = screeningService;
        this.bookingService = bookingService;
        this.pricingService = pricingService;

        setWidthFull();
        getStyle()
                .set("background", "#020b1d")
                .set("min-height", "100vh")
                .set("color", "white");

        configureFilters();
        configureDialogFields();

        Div page = new Div();
        page.getStyle()
                .set("max-width", "1320px")
                .set("margin", "0 auto")
                .set("padding", "44px 48px 80px 48px")
                .set("box-sizing", "border-box");

        page.add(hero, buildSearchBar(), showtimeArea);
        add(page);

        reloadScreenings();
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long filmId) {
        this.requestedFilmId = filmId;
        reloadScreenings();
    }

    private void configureFilters() {
        cinemaBox.setPlaceholder("Select a cinema");
        cinemaBox.setItems(cinemaRepository.findAll());
        cinemaBox.setItemLabelGenerator(Cinema::toString);
        cinemaBox.setClearButtonVisible(true);

        filmBox.setPlaceholder("Select a film");
        filmBox.setItemLabelGenerator(Film::getTitle);
        filmBox.setClearButtonVisible(false);

        datePicker.setPlaceholder("Select a date");
        datePicker.setValue(LocalDate.now());

        filmBox.addValueChangeListener(event -> {
            Film film = event.getValue();
            selectedFilmId = film == null ? null : film.getId();
            renderHero();
            renderShowtimes();
        });
    }

    private void configureDialogFields() {
        seatDialog.setModal(true);
        seatDialog.setCloseOnEsc(true);
        seatDialog.setCloseOnOutsideClick(false);
        seatDialog.setWidth("100vw");
        seatDialog.setHeight("100vh");

        customerNameField.setWidth("260px");
        customerNameField.setPlaceholder("e.g. John Smith");

        customerEmailField.setWidth("300px");
        customerEmailField.setPlaceholder("e.g. john@example.com");
        customerEmailField.setErrorMessage("Please enter a valid email address.");

        receiptArea.setWidthFull();
        receiptArea.setHeight("190px");
        receiptArea.setReadOnly(true);
        receiptArea.getStyle().set("margin-top", "24px");
    }

    private Div buildSearchBar() {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("background", "white")
                .set("color", "#111827")
                .set("padding", "18px 22px")
                .set("box-shadow", "0 16px 40px rgba(0,0,0,0.28)")
                .set("margin", "32px 0 50px 0")
                .set("display", "grid")
                .set("grid-template-columns", "1.2fr 1.2fr 1fr 220px")
                .set("gap", "14px")
                .set("align-items", "end");

        filmBox.setWidthFull();
        cinemaBox.setWidthFull();
        datePicker.setWidthFull();

        Button searchButton = new Button("Search", event -> reloadScreenings());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.getStyle()
                .set("height", "46px")
                .set("font-weight", "800")
                .set("background", "#0072ce")
                .set("border-radius", "0")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)");

        wrapper.add(filmBox, cinemaBox, datePicker, searchButton);
        return wrapper;
    }

    private void reloadScreenings() {
        LocalDate fromDate = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
        LocalDate toDate = fromDate.plusDays(7);

        Cinema selectedCinema = cinemaBox.getValue();

        if (selectedCinema == null) {
            currentScreenings = screeningService.findScreeningsBetween(fromDate, toDate);
        } else {
            currentScreenings = screeningService.findScreeningsByCinemaBetween(
                    selectedCinema.getId(),
                    fromDate,
                    toDate
            );
        }

        List<Film> films = currentScreenings.stream()
                .map(Screening::getFilm)
                .collect(Collectors.toMap(
                        Film::getId,
                        film -> film,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        filmBox.setItems(films);

        if (films.isEmpty()) {
            selectedFilmId = null;
            filmBox.clear();
        } else {
            Film selectedFilm = null;

            if (requestedFilmId != null) {
                selectedFilm = films.stream()
                        .filter(film -> Objects.equals(film.getId(), requestedFilmId))
                        .findFirst()
                        .orElse(null);
            }

            if (selectedFilm == null && selectedFilmId != null) {
                selectedFilm = films.stream()
                        .filter(film -> Objects.equals(film.getId(), selectedFilmId))
                        .findFirst()
                        .orElse(null);
            }

            if (selectedFilm == null) {
                selectedFilm = films.get(0);
            }

            selectedFilmId = selectedFilm.getId();
            filmBox.setValue(selectedFilm);
        }

        renderHero();
        renderShowtimes();
    }

    private void renderHero() {
        hero.removeAll();

        Screening firstScreening = findFirstScreeningForSelectedFilm();

        if (firstScreening == null) {
            Div empty = new Div();
            empty.setText("No film selected.");
            empty.getStyle()
                    .set("padding", "80px 0")
                    .set("font-size", "24px")
                    .set("color", "#cbd5e1");
            hero.add(empty);
            return;
        }

        Film film = firstScreening.getFilm();

        Div heroGrid = new Div();
        heroGrid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "240px 1fr 260px")
                .set("gap", "64px")
                .set("align-items", "start");

        Div posterBlock = new Div();
        posterBlock.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "18px");

        Div poster = buildPoster(film);

        Button bookButton = new Button("Book Tickets", event -> scrollToShowtimes());
        bookButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        bookButton.getStyle()
                .set("height", "48px")
                .set("background", "#0072ce")
                .set("border-radius", "0")
                .set("font-weight", "800")
                .set("clip-path", "polygon(0 0, 100% 0, 92% 100%, 0 100%)");

        Button trailerButton = new Button("Play trailer");
        trailerButton.getStyle()
                .set("height", "48px")
                .set("background", "transparent")
                .set("color", "white")
                .set("border", "1px solid white")
                .set("border-radius", "0")
                .set("font-weight", "700");

        posterBlock.add(poster, bookButton, trailerButton);

        Div info = new Div();

        H1 title = new H1(film.getTitle().toUpperCase());
        title.getStyle()
                .set("font-size", "44px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.03em")
                .set("margin", "22px 0 34px 0");

        Span age = new Span(film.getAgeRating());
        age.getStyle()
                .set("font-size", "14px")
                .set("background", "#ff4fa3")
                .set("border-radius", "999px")
                .set("padding", "6px 9px")
                .set("margin-left", "12px")
                .set("vertical-align", "middle");

        title.add(age);

        if (hasAdvancePreviewForSelectedFilm()) {
            title.add(heroBadge("ADVANCE PREVIEW"));
        }

        info.add(
                title,
                infoBlock("DIRECTORS", film.getDirectors()),
                infoBlock("CAST", film.getActors()),
                infoBlock("SYNOPSIS", film.getDescription())
        );

        Div side = new Div();
        side.getStyle()
                .set("padding-top", "82px")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "26px");

        side.add(
                infoBlock("RUNTIME", formatRuntime(film.getDurationMinutes())),
                infoBlock("GENRE", film.getGenre()),
                infoBlock("RELEASE DATE", formatReleaseDate(film.getReleaseDate())),
                infoBlock("CONTENT ADVICE", film.getContentAdvice()),
                infoBlock("BOOKING TYPE", hasAdvancePreviewForSelectedFilm()
                        ? "Advance preview screenings are available before or around the official release window."
                        : "Regular public screenings")
        );

        heroGrid.add(posterBlock, info, side);
        hero.add(heroGrid);
    }


    private Span heroBadge(String text) {
        Span badge = new Span(text);
        badge.getStyle()
                .set("display", "inline-block")
                .set("margin-left", "14px")
                .set("padding", "6px 10px")
                .set("border", "1px solid #fb923c")
                .set("color", "#fdba74")
                .set("font-size", "13px")
                .set("font-weight", "900")
                .set("vertical-align", "middle")
                .set("letter-spacing", "0.08em");
        return badge;
    }

    private Span showtimeBadge(String text) {
        Span badge = new Span(text);
        badge.getStyle()
                .set("display", "inline-block")
                .set("margin-top", "8px")
                .set("padding", "4px 7px")
                .set("border", "1px solid #fb923c")
                .set("color", "#fdba74")
                .set("font-size", "11px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.05em");
        return badge;
    }

    private boolean hasAdvancePreviewForSelectedFilm() {
        if (selectedFilmId == null) {
            return false;
        }

        return currentScreenings.stream()
                .filter(screening -> Objects.equals(screening.getFilm().getId(), selectedFilmId))
                .anyMatch(this::isAdvancePreview);
    }

    private boolean isAdvancePreview(Screening screening) {
        return screening != null && screening.getScreeningType() == ScreeningType.ADVANCE_PREVIEW;
    }

    private String screeningTypeLabel(Screening screening) {
        if (isAdvancePreview(screening)) {
            return "Advance Preview";
        }

        return "Regular";
    }

    private Div buildPoster(Film film) {
        Div poster = new Div();
        poster.getStyle()
                .set("width", "240px")
                .set("height", "360px")
                .set("background", "linear-gradient(145deg, #111827, #4c1d95)")
                .set("box-shadow", "0 18px 40px rgba(0,0,0,0.45)")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("text-align", "center")
                .set("font-weight", "900")
                .set("font-size", "20px")
                .set("overflow", "hidden");

        if (film.getPosterUrl() != null && !film.getPosterUrl().isBlank()) {
            Image image = new Image(film.getPosterUrl(), film.getTitle());
            image.setWidthFull();
            image.setHeightFull();
            image.getStyle().set("object-fit", "cover");
            poster.add(image);
        } else {
            poster.add(new Span(film.getTitle()));
        }

        return poster;
    }

    private Div infoBlock(String label, String value) {
        Div block = new Div();
        block.getStyle().set("margin-bottom", "22px");

        Span heading = new Span(label);
        heading.getStyle()
                .set("display", "block")
                .set("color", "#b9c4d4")
                .set("font-size", "15px")
                .set("letter-spacing", "0.04em")
                .set("margin-bottom", "8px");

        Paragraph text = new Paragraph(value == null || value.isBlank() ? "-" : value);
        text.getStyle()
                .set("margin", "0")
                .set("line-height", "1.55")
                .set("font-size", "16px")
                .set("color", "white");

        block.add(heading, text);
        return block;
    }

    private void renderShowtimes() {
        showtimeArea.removeAll();

        if (selectedFilmId == null) {
            return;
        }

        List<Screening> screenings = currentScreenings.stream()
                .filter(screening -> Objects.equals(screening.getFilm().getId(), selectedFilmId))
                .sorted(screeningComparator())
                .toList();

        if (screenings.isEmpty()) {
            Paragraph empty = new Paragraph("No showtimes are available for this film.");
            empty.getStyle().set("color", "#cbd5e1");
            showtimeArea.add(empty);
            return;
        }

        Map<String, List<Screening>> byCinema = screenings.stream()
                .collect(Collectors.groupingBy(
                        screening -> screening.getScreen().getCinema().getName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<String, List<Screening>> entry : byCinema.entrySet()) {
            showtimeArea.add(buildCinemaShowtimeSection(entry.getKey(), entry.getValue()));
        }
    }

    private Div buildCinemaShowtimeSection(String cinemaName, List<Screening> screenings) {
        Div section = new Div();
        section.getStyle().set("margin-bottom", "54px");

        H2 cinemaTitle = new H2(cinemaName.toUpperCase());
        cinemaTitle.getStyle()
                .set("font-size", "38px")
                .set("font-weight", "900")
                .set("letter-spacing", "0.03em")
                .set("margin", "0 0 22px 0");

        Span allShowtimes = new Span("All Showtimes");
        allShowtimes.getStyle()
                .set("display", "inline-block")
                .set("color", "#cbd5e1")
                .set("margin-bottom", "10px")
                .set("font-size", "15px");

        Div priceLine = new Div();
        priceLine.setText("Adult price from " + estimateLowestPrice(screenings));
        priceLine.getStyle()
                .set("font-size", "18px")
                .set("font-weight", "700")
                .set("margin-bottom", "22px");

        Div cardGrid = new Div();
        cardGrid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(160px, 1fr))")
                .set("gap", "22px");

        for (Screening screening : screenings) {
            cardGrid.add(buildShowtimeCard(screening));
        }

        section.add(cinemaTitle, allShowtimes, priceLine, cardGrid);
        return section;
    }

    private Div buildShowtimeCard(Screening screening) {
        Div card = new Div();
        card.getStyle()
                .set("border", "1px solid rgba(255,255,255,0.35)")
                .set("min-height", "116px")
                .set("display", "grid")
                .set("grid-template-rows", "1fr 38px")
                .set("background", "#020b1d")
                .set("box-shadow", "0 10px 28px rgba(0,0,0,0.2)");

        Div timeBlock = new Div();
        timeBlock.getStyle()
                .set("padding", "12px")
                .set("box-sizing", "border-box");

        Span time = new Span(screening.getStartTime().toString());
        time.getStyle()
                .set("display", "block")
                .set("font-size", "28px")
                .set("font-weight", "850")
                .set("line-height", "1");

        Span screen = new Span("Screen " + screening.getScreen().getScreenNumber());
        screen.getStyle()
                .set("display", "block")
                .set("color", "#cbd5e1")
                .set("margin-top", "8px")
                .set("font-size", "15px");

        timeBlock.add(time, screen);

        if (isAdvancePreview(screening)) {
            timeBlock.add(showtimeBadge("ADVANCE PREVIEW"));
        }

        Button bookNow = new Button(isAdvancePreview(screening) ? "Book Early" : "Book Now", event -> openSeatDialog(screening));
        bookNow.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        bookNow.getStyle()
                .set("border-radius", "0")
                .set("background", "#0072ce")
                .set("font-weight", "800")
                .set("height", "38px")
                .set("width", "100%")
                .set("clip-path", "polygon(0 0, 100% 0, 94% 100%, 0 100%)");

        card.add(timeBlock, bookNow);
        return card;
    }

    private void openSeatDialog(Screening screening) {
        selectedScreening = screening;
        selectedSeatIds.clear();
        seatById.clear();
        receiptArea.clear();

        loadSeatMap(screening);

        seatDialog.removeAll();

        Div content = new Div();
        content.getStyle()
                .set("width", "100vw")
                .set("height", "100vh")
                .set("background", "#020b1d")
                .set("color", "white")
                .set("padding", "40px")
                .set("box-sizing", "border-box")
                .set("overflow", "auto");

        Div top = new Div();
        top.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("align-items", "start")
                .set("margin-bottom", "36px");

        Div left = new Div();

        H1 title = new H1(screening.getFilm().getTitle().toUpperCase());
        title.getStyle()
                .set("font-size", "42px")
                .set("font-weight", "900")
                .set("margin", "0 0 28px 0");

        Button back = new Button("‹ Back", event -> seatDialog.close());
        back.getStyle()
                .set("background", "transparent")
                .set("color", "#38bdf8")
                .set("font-size", "20px")
                .set("font-weight", "700");

        left.add(title, back);

        Div right = new Div();
        right.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr 1fr")
                .set("gap", "28px")
                .set("justify-self", "end");

        right.add(
                dialogInfo("CINEMA", screening.getScreen().getCinema().getName()),
                dialogInfo("SHOWTIME", screening.getScreeningDate() + ", " + screening.getStartTime()),
                dialogInfo("SCREENING TYPE", screeningTypeLabel(screening))
        );

        top.add(left, right);

        Div steps = buildSteps();

        H2 choose = new H2("CHOOSE YOUR SEATS");
        choose.getStyle()
                .set("text-align", "center")
                .set("font-size", "32px")
                .set("letter-spacing", "0.12em")
                .set("margin", "44px 0 18px 0");

        Div customerRow = new Div();
        customerRow.getStyle()
                .set("display", "flex")
                .set("justify-content", "center")
                .set("gap", "12px")
                .set("flex-wrap", "wrap")
                .set("margin", "28px 0");

        customerRow.add(customerNameField, customerEmailField);

        Div priceBox = buildDialogPriceBox();

        Button createBookingButton = new Button("Create booking", event -> createBooking());
        createBookingButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createBookingButton.getStyle()
                .set("height", "46px")
                .set("background", "#0072ce")
                .set("font-weight", "900")
                .set("border-radius", "0")
                .set("margin-top", "18px")
                .set("width", "220px");

        Div actionRow = new Div(createBookingButton);
        actionRow.getStyle()
                .set("display", "flex")
                .set("justify-content", "center");

        content.add(
                top,
                steps,
                choose,
                seatMap,
                customerRow,
                priceBox,
                actionRow,
                receiptArea
        );

        seatDialog.add(content);
        seatDialog.open();
    }

    private Div dialogInfo(String label, String value) {
        Div box = new Div();

        Span h = new Span(label);
        h.getStyle()
                .set("display", "block")
                .set("color", "#94a3b8")
                .set("font-size", "16px")
                .set("letter-spacing", "0.06em")
                .set("font-weight", "700");

        Span v = new Span(value);
        v.getStyle()
                .set("display", "block")
                .set("font-size", "17px")
                .set("font-weight", "700")
                .set("margin-top", "8px");

        box.add(h, v);
        return box;
    }

    private Div buildSteps() {
        Div steps = new Div();
        steps.getStyle()
                .set("display", "flex")
                .set("justify-content", "center")
                .set("gap", "56px")
                .set("color", "#64748b")
                .set("font-weight", "800")
                .set("font-size", "18px");

        steps.add(
                step("①", "Seats", true),
                step("②", "Tickets", false),
                step("③", "Summary", false),
                step("④", "Payment", false)
        );

        return steps;
    }

    private Div step(String icon, String text, boolean active) {
        Div item = new Div();
        item.getStyle()
                .set("text-align", "center")
                .set("color", active ? "white" : "#64748b");

        Span i = new Span(icon);
        i.getStyle()
                .set("display", "block")
                .set("font-size", "28px")
                .set("margin-bottom", "8px");

        Span t = new Span(text);
        item.add(i, t);
        return item;
    }

    private Div buildDialogPriceBox() {
        Div priceBox = new Div();
        priceBox.getStyle()
                .set("max-width", "760px")
                .set("margin", "28px auto")
                .set("padding", "18px")
                .set("border", "1px solid rgba(255,255,255,0.18)")
                .set("background", "rgba(255,255,255,0.04)");

        selectedSeatChips.getStyle()
                .set("display", "flex")
                .set("gap", "8px")
                .set("flex-wrap", "wrap")
                .set("justify-content", "center")
                .set("min-height", "32px");

        Div totalRow = new Div();
        totalRow.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("margin-top", "16px")
                .set("padding-top", "16px")
                .set("border-top", "1px solid rgba(255,255,255,0.18)");

        Span totalText = new Span("Total price");
        totalText.getStyle().set("font-weight", "900");

        totalPriceLabel.getStyle()
                .set("font-weight", "900")
                .set("font-size", "24px")
                .set("color", "#38bdf8");

        totalRow.add(totalText, totalPriceLabel);
        priceBox.add(selectedSeatChips, totalRow);

        updatePriceSummary();
        return priceBox;
    }

    private void loadSeatMap(Screening screening) {
        currentSeatOptions = bookingService.findSeatOptions(screening.getId())
                .stream()
                .sorted(seatOptionComparator())
                .toList();

        seatById.clear();

        for (BookingService.SeatOption option : currentSeatOptions) {
            seatById.put(option.seat().getId(), option.seat());
        }

        renderSeatMap();
    }

    private void renderSeatMap() {
        seatMap.removeAll();
        seatMap.getStyle()
                .set("max-width", "760px")
                .set("margin", "0 auto")
                .set("padding", "28px")
                .set("border-radius", "18px")
                .set("background", "#06152e")
                .set("overflow-x", "auto");

        Div screen = new Div();
        screen.setText("SCREEN");
        screen.getStyle()
                .set("height", "34px")
                .set("line-height", "34px")
                .set("text-align", "center")
                .set("border-radius", "50% 50% 8px 8px")
                .set("background", "linear-gradient(180deg, #dbeafe, #93c5fd)")
                .set("color", "#1e3a8a")
                .set("font-weight", "900")
                .set("letter-spacing", "4px")
                .set("margin", "0 auto 24px auto")
                .set("max-width", "520px");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(10, 44px)")
                .set("gap", "10px")
                .set("justify-content", "center")
                .set("min-width", "540px");

        for (BookingService.SeatOption option : currentSeatOptions) {
            grid.add(createSeatButton(option));
        }

        seatMap.add(screen, buildSeatLegend(), grid);
        updatePriceSummary();
    }

    private Button createSeatButton(BookingService.SeatOption option) {
        Seat seat = option.seat();
        boolean selected = selectedSeatIds.contains(seat.getId());

        Button button = new Button(seat.getSeatNumber());
        button.setWidth("44px");
        button.setHeight("38px");

        button.getStyle()
                .set("border-radius", "10px")
                .set("font-size", "13px")
                .set("font-weight", "800")
                .set("padding", "0")
                .set("cursor", option.available() ? "pointer" : "not-allowed")
                .set("box-shadow", "0 3px 8px rgba(0,0,0,0.18)");

        if (!option.available()) {
            button.setEnabled(false);
            button.getStyle()
                    .set("background", "#475569")
                    .set("color", "#94a3b8")
                    .set("border", "1px solid #475569");
            return button;
        }

        if (selected) {
            button.getStyle()
                    .set("background", "#0072ce")
                    .set("color", "white")
                    .set("border", "1px solid #38bdf8");
        } else {
            button.getStyle()
                    .set("background", "white")
                    .set("color", "#111827")
                    .set("border", "1px solid #d1d5db");
        }

        button.addClickListener(event -> {
            if (selectedSeatIds.contains(seat.getId())) {
                selectedSeatIds.remove(seat.getId());
            } else {
                selectedSeatIds.add(seat.getId());
            }

            renderSeatMap();
        });

        return button;
    }

    private HorizontalLayout buildSeatLegend() {
        HorizontalLayout legend = new HorizontalLayout(
                legendItem("#ffffff", "Available"),
                legendItem("#0072ce", "Selected"),
                legendItem("#475569", "Booked")
        );

        legend.setWidthFull();
        legend.setJustifyContentMode(JustifyContentMode.CENTER);
        legend.getStyle()
                .set("gap", "18px")
                .set("margin-bottom", "20px")
                .set("flex-wrap", "wrap");

        return legend;
    }

    private Div legendItem(String color, String text) {
        Div item = new Div();
        item.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "8px")
                .set("font-size", "13px")
                .set("color", "#e5e7eb");

        Span dot = new Span();
        dot.getStyle()
                .set("width", "16px")
                .set("height", "16px")
                .set("border-radius", "6px")
                .set("background", color)
                .set("display", "inline-block")
                .set("border", "1px solid #d1d5db");

        item.add(dot, new Span(text));
        return item;
    }

    private void createBooking() {
        if (selectedScreening == null) {
            Notification.show("Please select a screening.");
            return;
        }

        if (selectedSeatIds.isEmpty()) {
            Notification.show("Please select at least one seat.");
            return;
        }

        List<Seat> selectedSeats = selectedSeatIds.stream()
                .map(seatById::get)
                .filter(Objects::nonNull)
                .sorted(seatComparator())
                .toList();

        try {
            Booking booking = bookingService.createBooking(
                    selectedScreening.getId(),
                    selectedSeatIds,
                    customerNameField.getValue(),
                    customerEmailField.getValue()
            );

            receiptArea.setValue(buildReceipt(booking, selectedScreening, selectedSeats));
            Notification.show("Booking created successfully.");

            selectedSeatIds.clear();
            loadSeatMap(selectedScreening);
            reloadScreenings();

        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private void updatePriceSummary() {
        selectedSeatChips.removeAll();

        if (selectedScreening == null || selectedSeatIds.isEmpty()) {
            Span empty = new Span("No seats selected");
            empty.getStyle().set("color", "#94a3b8");
            selectedSeatChips.add(empty);
            totalPriceLabel.setText("£0.00");
            return;
        }

        List<Seat> selectedSeats = selectedSeatIds.stream()
                .map(seatById::get)
                .filter(Objects::nonNull)
                .sorted(seatComparator())
                .toList();

        for (Seat seat : selectedSeats) {
            Span chip = new Span(seat.getSeatNumber());
            chip.getStyle()
                    .set("background", "#dbeafe")
                    .set("color", "#1e40af")
                    .set("padding", "6px 10px")
                    .set("border-radius", "999px")
                    .set("font-weight", "800")
                    .set("font-size", "13px");

            selectedSeatChips.add(chip);
        }

        BigDecimal total = pricingService.calculateTotalPrice(selectedScreening, selectedSeats);
        totalPriceLabel.setText(formatMoney(total));
    }

    private Screening findFirstScreeningForSelectedFilm() {
        if (selectedFilmId == null) {
            return null;
        }

        return currentScreenings.stream()
                .filter(screening -> Objects.equals(screening.getFilm().getId(), selectedFilmId))
                .findFirst()
                .orElse(null);
    }

    private String estimateLowestPrice(List<Screening> screenings) {
        return screenings.stream()
                .map(screening -> {
                    List<BookingService.SeatOption> options = bookingService.findSeatOptions(screening.getId());

                    return options.stream()
                            .filter(BookingService.SeatOption::available)
                            .map(option -> pricingService.calculateTicketPrice(screening, option.seat()))
                            .min(BigDecimal::compareTo)
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .map(this::formatMoney)
                .orElse("Sold out");
    }

    private void scrollToShowtimes() {
        showtimeArea.getElement().executeJs(
                "this.scrollIntoView({behavior: 'smooth', block: 'start'});"
        );
    }

    private String buildReceipt(
            Booking booking,
            Screening screening,
            List<Seat> selectedSeats
    ) {
        String seatNumbers = selectedSeats.stream()
                .map(Seat::getSeatNumber)
                .collect(Collectors.joining(", "));

        return """
                Booking Reference: %s
                Film: %s
                Cinema: %s
                City: %s
                Date: %s
                Showing Time: %s
                Screen: %s
                Number of Tickets: %d
                Seat Numbers: %s
                Total Booking Cost: %s
                Booking Date: %s
                Screening Type: %s
                Status: %s
                """.formatted(
                booking.getBookingReference(),
                screening.getFilm().getTitle(),
                screening.getScreen().getCinema().getName(),
                screening.getScreen().getCinema().getCity(),
                screening.getScreeningDate(),
                screening.getStartTime(),
                screening.getScreen().getScreenNumber(),
                selectedSeats.size(),
                seatNumbers,
                formatMoney(booking.getTotalCost()),
                booking.getBookingDate(),
                screeningTypeLabel(screening),
                booking.getStatus()
        );
    }

    private Comparator<Screening> screeningComparator() {
        return Comparator
                .comparing((Screening screening) -> screening.getScreen().getCinema().getName())
                .thenComparing(Screening::getScreeningDate)
                .thenComparing(Screening::getStartTime);
    }

    private Comparator<BookingService.SeatOption> seatOptionComparator() {
        return Comparator
                .comparingInt((BookingService.SeatOption option) -> rowIndex(option.seat().getSeatNumber()))
                .thenComparingInt(option -> seatNumberValue(option.seat().getSeatNumber()));
    }

    private Comparator<Seat> seatComparator() {
        return Comparator
                .comparingInt((Seat seat) -> rowIndex(seat.getSeatNumber()))
                .thenComparingInt(seat -> seatNumberValue(seat.getSeatNumber()));
    }

    private int rowIndex(String seatNumber) {
        if (seatNumber == null || seatNumber.isBlank()) {
            return 999;
        }

        return Character.toUpperCase(seatNumber.charAt(0)) - 'A';
    }

    private int seatNumberValue(String seatNumber) {
        if (seatNumber == null) {
            return 0;
        }

        String digits = seatNumber.replaceAll("\\D+", "");

        if (digits.isBlank()) {
            return 0;
        }

        return Integer.parseInt(digits);
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.UK).format(amount);
    }
    private String formatRuntime(int durationMinutes) {
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;

        if (hours <= 0) {
            return minutes + " min";
        }

        return hours + "h " + minutes + "m";
    }

    private String formatReleaseDate(LocalDate releaseDate) {
        if (releaseDate == null) {
            return "Available soon";
        }

        return releaseDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK));
    }
}