package com.example.ticketservice.application.usecase;

import com.example.ticketservice.application.dto.response.MovieScheduleResponse;
import com.example.ticketservice.application.dto.response.TicketableScheduleResponse;
import com.example.ticketservice.common.model.PageResult;
import com.example.ticketservice.domain.enums.ScheduleStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

public interface ScheduleQueryUseCase {
    PageResult<TicketableScheduleResponse> listOpenSchedules(Set<ScheduleStatus> statusFilter, Pageable pageable);

    List<MovieScheduleResponse> listSchedulesByMovieId(Long movieId);
}