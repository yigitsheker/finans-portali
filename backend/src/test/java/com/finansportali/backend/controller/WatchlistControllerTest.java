package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.alert.WatchlistDto;
import com.finansportali.backend.service.WatchlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
class WatchlistControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private WatchlistService service;

    private WatchlistDto sample() {
        return new WatchlistDto(1L, "My List",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                LocalDateTime.of(2026, 5, 19, 10, 0),
                List.of("THYAO", "AAPL"));
    }

    @Test
    void list_returns_user_watchlists() throws Exception {
        when(service.getUserWatchlists("user-1")).thenReturn(List.of(sample()));

        mvc.perform(get("/api/v1/watchlists").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].symbols[0]").value("THYAO"));
    }

    @Test
    void get_one_routes_id_and_user() throws Exception {
        when(service.getWatchlist("user-1", 7L)).thenReturn(sample());

        mvc.perform(get("/api/v1/watchlists/7").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(service).getWatchlist("user-1", 7L);
    }

    @Test
    void create_with_name_returns_dto() throws Exception {
        when(service.createWatchlist("user-1", "Favs")).thenReturn(sample());

        mvc.perform(post("/api/v1/watchlists")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Favs\"}"))
                .andExpect(status().isOk());

        verify(service).createWatchlist("user-1", "Favs");
    }

    @Test
    void update_passes_id_name_user() throws Exception {
        when(service.updateWatchlist(eq("user-1"), anyLong(), anyString())).thenReturn(sample());

        mvc.perform(put("/api/v1/watchlists/3")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk());

        verify(service).updateWatchlist("user-1", 3L, "Renamed");
    }

    @Test
    void delete_returns_204() throws Exception {
        mvc.perform(delete("/api/v1/watchlists/5")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).deleteWatchlist("user-1", 5L);
    }

    @Test
    void add_item_routes_payload() throws Exception {
        mvc.perform(post("/api/v1/watchlists/items")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":2,\"symbol\":\"THYAO\"}"))
                .andExpect(status().isOk());

        verify(service).addToWatchlist("user-1", 2L, "THYAO");
    }

    @Test
    void remove_item_routes_path_vars() throws Exception {
        mvc.perform(delete("/api/v1/watchlists/2/items/THYAO")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).removeFromWatchlist("user-1", 2L, "THYAO");
    }
}
