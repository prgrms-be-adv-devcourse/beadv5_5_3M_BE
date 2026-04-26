package com.example.ticketservice.application.service;

import com.example.ticketservice.application.constants.RedisKeys;
import com.example.ticketservice.application.dto.response.MovieScheduleResponse;
import com.example.ticketservice.application.dto.response.TicketableScheduleResponse;
import com.example.ticketservice.application.port.out.CachePort;
import com.example.ticketservice.application.usecase.ScheduleQueryUseCase;
import com.example.ticketservice.common.exception.ScheduleErrorCode;
import com.example.ticketservice.common.model.PageResult;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import com.example.ticketservice.domain.model.Schedule;
import com.example.ticketservice.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService implements ScheduleQueryUseCase {

    private static final Set<ScheduleStatus> DEFAULT_OPEN_STATUSES =
            EnumSet.of(ScheduleStatus.CART, ScheduleStatus.IN_PROGRESSING, ScheduleStatus.TICKETING);

    private static final Set<ScheduleStatus> MOVIE_DETAIL_STATUSES =
            EnumSet.of(ScheduleStatus.CART, ScheduleStatus.IN_PROGRESSING,
                    ScheduleStatus.TICKETING, ScheduleStatus.STREAMING);

    private final ScheduleRepository scheduleRepository;
    private final CachePort cachePort;

    @Transactional(readOnly = true)
    @Override
    public PageResult<TicketableScheduleResponse> listOpenSchedules(Set<ScheduleStatus> statusFilter, Pageable pageable) {
        Set<ScheduleStatus> resolved = resolveAndValidate(statusFilter);

        Page<Schedule> page = scheduleRepository.findAllByStatusIn(resolved, pageable);

        Map<Long, Integer> stockByScheduleId = loadStockForTicketing(page.getContent());

        Page<TicketableScheduleResponse> mapped = page.map(schedule ->
                TicketableScheduleResponse.from(schedule, stockByScheduleId.get(schedule.getId())));

        return PageResult.from(mapped);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieScheduleResponse> listSchedulesByMovieId(Long movieId) {
        List<Schedule> schedules = scheduleRepository
                .findAllByMovieIdAndStatusInOrderByStartTimeAsc(movieId, MOVIE_DETAIL_STATUSES);

        Map<Long, Integer> stockByScheduleId = loadStockForTicketing(schedules);

        return schedules.stream()
                .map(s -> MovieScheduleResponse.from(s, stockByScheduleId.get(s.getId())))
                .toList();
    }

    private Set<ScheduleStatus> resolveAndValidate(Set<ScheduleStatus> statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            return DEFAULT_OPEN_STATUSES;
        }
        for (ScheduleStatus s : statusFilter) {
            if (!DEFAULT_OPEN_STATUSES.contains(s)) {
                throw ScheduleErrorCode.INVALID_STATUS_FILTER.of();
            }
        }
        return statusFilter;
    }

    private Map<Long, Integer> loadStockForTicketing(List<Schedule> schedules) {
        List<Long> ticketingIds = schedules.stream()
                .filter(s -> s.getStatus() == ScheduleStatus.TICKETING)
                .map(Schedule::getId)
                .toList();

        if (ticketingIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = ticketingIds.stream()
                .map(id -> RedisKeys.STOCK + id)
                .toList();

        Map<String, Long> raw = cachePort.getCounters(keys);

        return ticketingIds.stream()
                .filter(id -> raw.get(RedisKeys.STOCK + id) != null)
                .collect(java.util.stream.Collectors.toMap(
                        id -> id,
                        id -> raw.get(RedisKeys.STOCK + id).intValue()
                ));
    }
}