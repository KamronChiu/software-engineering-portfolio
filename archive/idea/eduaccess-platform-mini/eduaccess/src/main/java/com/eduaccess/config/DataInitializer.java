package com.eduaccess.config;

import com.eduaccess.domain.Screen;
import com.eduaccess.domain.Seat;
import com.eduaccess.domain.SeatType;
import com.eduaccess.repository.ScreenRepository;
import com.eduaccess.repository.SeatRepository;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class DataInitializer {

    private static final int SEATS_PER_ROW = 10;

    @Bean
    CommandLineRunner maintainSeedData(
            ScreenRepository screenRepository,
            SeatRepository seatRepository,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager
    ) {
        return args -> {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

            transactionTemplate.executeWithoutResult(status -> {
                int deletedScreenings = deleteExpiredUnbookedScreenings(entityManager);
                ensureSeatsExistForAllScreens(screenRepository, seatRepository);

                System.out.println("HCBS data maintenance completed.");
                System.out.println("Expired unbooked screenings removed: " + deletedScreenings);
            });
        };
    }

    private int deleteExpiredUnbookedScreenings(EntityManager entityManager) {
        /*
         * Safe cleanup rule:
         * - Delete only screenings before today.
         * - Do not delete screenings that already have bookings.
         *
         * This preserves booking history and avoids foreign-key errors.
         */
        return entityManager.createNativeQuery("""
                DELETE FROM screenings s
                WHERE s.screening_date < CURRENT_DATE
                  AND NOT EXISTS (
                      SELECT 1
                      FROM bookings b
                      WHERE b.screening_id = s.id
                  )
                """).executeUpdate();
    }

    private void ensureSeatsExistForAllScreens(
            ScreenRepository screenRepository,
            SeatRepository seatRepository
    ) {
        List<Screen> screens = screenRepository.findAll();

        for (Screen screen : screens) {
            List<Seat> existingSeats = seatRepository.findByScreenIdOrderBySeatNumberAsc(screen.getId());

            Set<String> existingSeatNumbers = new HashSet<>();
            for (Seat seat : existingSeats) {
                existingSeatNumbers.add(seat.getSeatNumber());
            }

            List<Seat> missingSeats = generateMissingSeats(screen, existingSeatNumbers);

            if (!missingSeats.isEmpty()) {
                seatRepository.saveAll(missingSeats);
                System.out.println(
                        "Generated " + missingSeats.size()
                                + " missing seats for Screen "
                                + screen.getScreenNumber()
                                + " at "
                                + screen.getCinema().getName()
                );
            }
        }
    }

    private List<Seat> generateMissingSeats(Screen screen, Set<String> existingSeatNumbers) {
        List<Seat> seats = new ArrayList<>();
        int capacity = screen.getCapacity();

        for (int i = 0; i < capacity; i++) {
            String label = buildSeatLabel(i);

            if (existingSeatNumbers.contains(label)) {
                continue;
            }

            SeatType seatType = i < capacity * 0.7
                    ? SeatType.LOWER_HALL
                    : SeatType.UPPER_GALLERY;

            seats.add(new Seat(screen, label, seatType));
        }

        return seats;
    }

    private String buildSeatLabel(int index) {
        char row = (char) ('A' + (index / SEATS_PER_ROW));
        int seatNumber = (index % SEATS_PER_ROW) + 1;

        return row + String.valueOf(seatNumber);
    }
}