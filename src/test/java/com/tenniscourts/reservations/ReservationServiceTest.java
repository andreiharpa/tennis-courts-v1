package com.tenniscourts.reservations;

import com.tenniscourts.guests.Guest;
import com.tenniscourts.schedules.Schedule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(MockitoJUnitRunner.class)
public class ReservationServiceTest {
    private static final Long TEST_SCHEDULE_ID = 1L;
    private static final Long TEST_SCHEDULE_ID_2 = 2L;
    private static final Long TEST_GUEST_ID = 1L;
    private static final Long TEST_RESERVATION_ID = 1L;
    private LocalDateTime tomorrow;
    private LocalDateTime yesterday;

    @Mock
    ReservationRepository reservationRepo;

    @Mock
    ReservationMapper reservationMapper;

    @InjectMocks
    @Spy
    ReservationService reservationService;

    @Before
    public void before() {
        this.tomorrow = LocalDateTime.now().plusDays(1);
        this.yesterday = LocalDateTime.now().minusDays(1);
    }

    @Test
    public void bookReservation() {
        CreateReservationRequestDTO createReservationRequestDTO =
                new CreateReservationRequestDTO(TEST_GUEST_ID, TEST_SCHEDULE_ID);
        Reservation testReservation = getTestReservation(ReservationStatus.READY_TO_PLAY, TEST_SCHEDULE_ID, this.tomorrow);
        ReservationDTO testReservationDTO = getTestReservationDto(TEST_SCHEDULE_ID, ReservationStatus.READY_TO_PLAY, null);
        when(reservationMapper.map(createReservationRequestDTO)).thenReturn(testReservation);
        when(reservationRepo.save(testReservation)).thenReturn(testReservation);
        when(reservationMapper.map(testReservation)).thenReturn(testReservationDTO);

        ReservationDTO result = reservationService.bookReservation(createReservationRequestDTO);
        assertEquals(result, testReservationDTO );

        verify(reservationRepo).save(testReservation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelReservationWithCanceledStatus() {
        Reservation testReservation = getTestReservation(ReservationStatus.CANCELLED, TEST_SCHEDULE_ID, this.tomorrow);
        when(reservationRepo.findById(TEST_RESERVATION_ID)).thenReturn(Optional.of(testReservation));
        reservationService.cancelReservation(TEST_RESERVATION_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void cancelReservationWithDateInPast() {
        Reservation testReservation = getTestReservation(ReservationStatus.READY_TO_PLAY, TEST_SCHEDULE_ID, yesterday);
        when(reservationRepo.findById(TEST_RESERVATION_ID)).thenReturn(Optional.of(testReservation));
        reservationService.cancelReservation(TEST_RESERVATION_ID);
    }

    @Test
    public void cancelReservation() {
        Reservation testReservation = getTestReservation(ReservationStatus.READY_TO_PLAY, TEST_SCHEDULE_ID, this.tomorrow);
        Reservation canceledReservation = getTestCanceledReservation(ReservationStatus.READY_TO_PLAY, this.tomorrow);

        when(reservationService.getRefundValue(testReservation)).thenReturn(ReservationService.RESERVATION_PRICE);
        when(reservationRepo.findById(TEST_RESERVATION_ID)).thenReturn(Optional.of(testReservation));
        when(reservationRepo.save(canceledReservation)).thenReturn(canceledReservation);

        reservationService.cancelReservation(TEST_RESERVATION_ID);
        verify(reservationRepo).save(canceledReservation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rescheduleReservationWithCanceledStatus() {
        Reservation testReservation = getTestReservation(ReservationStatus.CANCELLED, TEST_SCHEDULE_ID, this.tomorrow);
        when(reservationRepo.findById(TEST_RESERVATION_ID)).thenReturn(Optional.of(testReservation));
        reservationService.rescheduleReservation(TEST_RESERVATION_ID, TEST_SCHEDULE_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rescheduleReservationWithDateInPast() {
        Reservation testReservation = getTestReservation(ReservationStatus.READY_TO_PLAY, TEST_SCHEDULE_ID, this.tomorrow);
        when(reservationRepo.findById(TEST_RESERVATION_ID)).thenReturn(Optional.of(testReservation));
        reservationService.rescheduleReservation(TEST_RESERVATION_ID, TEST_SCHEDULE_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rescheduleReservationWithSameScheduleId() {
        Reservation testReservation = getTestReservation(ReservationStatus.READY_TO_PLAY, TEST_SCHEDULE_ID, this.tomorrow);
        when(reservationRepo.findById(TEST_RESERVATION_ID)).thenReturn(Optional.of(testReservation));
        reservationService.rescheduleReservation(TEST_RESERVATION_ID, TEST_SCHEDULE_ID);
    }

    @Test
    public void rescheduleReservation() {
        Reservation testReservation = getTestReservation(ReservationStatus.READY_TO_PLAY, TEST_SCHEDULE_ID, this.tomorrow);
        Reservation canceledReservation = getTestCanceledReservation(ReservationStatus.READY_TO_PLAY, this.tomorrow);
        ReservationDTO canceledReservationDTO = getTestReservationDto(TEST_SCHEDULE_ID, ReservationStatus.RESCHEDULED, null);
        ReservationDTO reservationDTO = getTestReservationDto(TEST_SCHEDULE_ID_2, ReservationStatus.READY_TO_PLAY, null);

        when(reservationService.getRefundValue(testReservation)).thenReturn(ReservationService.RESERVATION_PRICE);
        when(reservationRepo.findById(TEST_RESERVATION_ID)).thenReturn(Optional.of(testReservation));
        when(reservationRepo.save(canceledReservation)).thenReturn(canceledReservation);
        doReturn(reservationDTO).when(reservationService).bookReservation(any());
        when(reservationMapper.map(canceledReservation)).thenReturn(canceledReservationDTO);

        ReservationDTO rescheduledReservationDto = reservationService.rescheduleReservation(TEST_RESERVATION_ID, TEST_SCHEDULE_ID_2);

        verify(reservationRepo).save(canceledReservation);

        canceledReservation.setReservationStatus(ReservationStatus.RESCHEDULED);

        verify(reservationRepo).save(canceledReservation);
        verify(reservationService).bookReservation(new CreateReservationRequestDTO(TEST_GUEST_ID, TEST_SCHEDULE_ID_2));

        assertEquals(getTestReservationDto(TEST_SCHEDULE_ID_2, ReservationStatus.READY_TO_PLAY, canceledReservationDTO), rescheduledReservationDto);
    }

    @Test
    public void getRefundValueFullRefund() {
        Schedule schedule = new Schedule();

        LocalDateTime startDateTime = LocalDateTime.now().plusDays(2);

        schedule.setStartDateTime(startDateTime);

        Assert.assertEquals(reservationService.getRefundValue(Reservation.builder().schedule(schedule).value(new BigDecimal(10L)).build()), new BigDecimal(10));
    }

    @Test
    public void getRefundValueNoRefund() {
        Schedule schedule = new Schedule();

        LocalDateTime startDateTime = LocalDateTime.now().plusHours(2);

        schedule.setStartDateTime(startDateTime);

        Assert.assertEquals(reservationService.getRefundValue(Reservation.builder().schedule(schedule).value(new BigDecimal(10L)).build()), new BigDecimal(0));
    }

    private Reservation getTestCanceledReservation(ReservationStatus reservationStatus, LocalDateTime startDateTime) {
        Reservation reservation = getTestReservation(reservationStatus, TEST_SCHEDULE_ID, startDateTime);
        reservation.setValue(reservation.getValue().subtract(ReservationService.RESERVATION_PRICE));
        reservation.setRefundValue(ReservationService.RESERVATION_PRICE);
        reservation.setReservationStatus(ReservationStatus.CANCELLED);
        return reservation;
    }

    private Reservation getTestReservation(ReservationStatus reservationStatus, Long scheduleId, LocalDateTime startDateTime) {
        Guest guest = new Guest();
        guest.setId(TEST_GUEST_ID);
        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setStartDateTime(startDateTime);
        return Reservation.builder()
                .guest(guest)
                .schedule(schedule)
                .reservationStatus(reservationStatus)
                .value(ReservationService.RESERVATION_PRICE)
                .build();
    }

    private ReservationDTO getTestReservationDto(Long scheduleId, ReservationStatus reservationStatus, ReservationDTO previousReservation) {
        return ReservationDTO.builder()
                .guestId(TEST_GUEST_ID)
                .scheduledId(scheduleId)
                .reservationStatus(reservationStatus.toString())
                .previousReservation(previousReservation)
                .value(ReservationService.RESERVATION_PRICE)
                .build();
    }
}
