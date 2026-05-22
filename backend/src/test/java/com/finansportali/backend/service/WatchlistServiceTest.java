package com.finansportali.backend.service;

import com.finansportali.backend.dto.response.alert.WatchlistDto;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.Watchlist;
import com.finansportali.backend.entity.WatchlistItem;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.WatchlistItemRepository;
import com.finansportali.backend.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistRepository watchlistRepo;
    @Mock private WatchlistItemRepository itemRepo;
    @Mock private MarketInstrumentRepository instrumentRepo;
    @InjectMocks private WatchlistService service;

    private static Watchlist wl(Long id, String userId, String name) {
        Watchlist w = new Watchlist(userId, name);
        w.setId(id);
        return w;
    }

    private static MarketInstrument inst(String symbol) {
        MarketInstrument i = new MarketInstrument();
        i.setSymbol(symbol);
        return i;
    }

    @Test
    void getUserWatchlists_returns_dtos_with_symbols() {
        Watchlist w = wl(1L, "u", "Favs");
        when(watchlistRepo.findByUserIdOrderByCreatedAtDesc("u")).thenReturn(List.of(w));
        when(itemRepo.findByWatchlistIdOrderByAddedAtDesc(1L)).thenReturn(List.of(
                new WatchlistItem(1L, "THYAO"), new WatchlistItem(1L, "AKBNK")));

        List<WatchlistDto> dtos = service.getUserWatchlists("u");
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).name()).isEqualTo("Favs");
        assertThat(dtos.get(0).symbols()).containsExactly("THYAO", "AKBNK");
    }

    @Test
    void getWatchlist_throws_when_not_found() {
        when(watchlistRepo.findByIdAndUserId(99L, "u")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getWatchlist("u", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getWatchlist_returns_dto() {
        Watchlist w = wl(1L, "u", "Favs");
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.of(w));
        when(itemRepo.findByWatchlistIdOrderByAddedAtDesc(1L)).thenReturn(List.of());
        assertThat(service.getWatchlist("u", 1L).name()).isEqualTo("Favs");
    }

    @Test
    void createWatchlist_saves_and_returns_dto() {
        Watchlist saved = wl(5L, "u", "New");
        when(watchlistRepo.save(any(Watchlist.class))).thenReturn(saved);
        when(itemRepo.findByWatchlistIdOrderByAddedAtDesc(5L)).thenReturn(List.of());

        WatchlistDto dto = service.createWatchlist("u", "New");

        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.name()).isEqualTo("New");
        ArgumentCaptor<Watchlist> cap = ArgumentCaptor.forClass(Watchlist.class);
        verify(watchlistRepo).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo("u");
    }

    @Test
    void updateWatchlist_throws_when_not_owned() {
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateWatchlist("u", 1L, "X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateWatchlist_updates_name_and_saves() {
        Watchlist w = wl(1L, "u", "Old");
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.of(w));
        when(watchlistRepo.save(w)).thenReturn(w);
        when(itemRepo.findByWatchlistIdOrderByAddedAtDesc(1L)).thenReturn(List.of());

        service.updateWatchlist("u", 1L, "New name");
        assertThat(w.getName()).isEqualTo("New name");
    }

    @Test
    void deleteWatchlist_throws_when_repo_returns_zero() {
        when(watchlistRepo.deleteByIdAndUserId(1L, "u")).thenReturn(0L);
        assertThatThrownBy(() -> service.deleteWatchlist("u", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(itemRepo).deleteByWatchlistId(1L);
    }

    @Test
    void deleteWatchlist_deletes_items_then_watchlist() {
        when(watchlistRepo.deleteByIdAndUserId(1L, "u")).thenReturn(1L);
        service.deleteWatchlist("u", 1L);
        verify(itemRepo).deleteByWatchlistId(1L);
        verify(watchlistRepo).deleteByIdAndUserId(1L, "u");
    }

    @Test
    void addToWatchlist_throws_when_watchlist_missing() {
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addToWatchlist("u", 1L, "THYAO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Watchlist not found");
    }

    @Test
    void addToWatchlist_throws_when_symbol_unknown() {
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.of(wl(1L, "u", "F")));
        when(instrumentRepo.findBySymbol("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addToWatchlist("u", 1L, "XXX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown symbol");
    }

    @Test
    void addToWatchlist_skips_save_when_symbol_already_in_list() {
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.of(wl(1L, "u", "F")));
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(inst("THYAO")));
        when(itemRepo.findByWatchlistIdAndSymbol(1L, "THYAO"))
                .thenReturn(Optional.of(new WatchlistItem(1L, "THYAO")));

        service.addToWatchlist("u", 1L, "THYAO");
        verify(itemRepo, never()).save(any());
    }

    @Test
    void addToWatchlist_saves_new_item() {
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.of(wl(1L, "u", "F")));
        when(instrumentRepo.findBySymbol("THYAO")).thenReturn(Optional.of(inst("THYAO")));
        when(itemRepo.findByWatchlistIdAndSymbol(1L, "THYAO")).thenReturn(Optional.empty());

        service.addToWatchlist("u", 1L, "THYAO");

        ArgumentCaptor<WatchlistItem> cap = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(itemRepo).save(cap.capture());
        assertThat(cap.getValue().getSymbol()).isEqualTo("THYAO");
    }

    @Test
    void removeFromWatchlist_throws_when_watchlist_missing() {
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.removeFromWatchlist("u", 1L, "THYAO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeFromWatchlist_succeeds_silently_when_deletion_count_zero() {
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.of(wl(1L, "u", "F")));
        when(itemRepo.deleteByWatchlistIdAndSymbol(1L, "THYAO")).thenReturn(0L);
        service.removeFromWatchlist("u", 1L, "THYAO"); // no throw
    }

    @Test
    void removeFromWatchlist_deletes_when_present() {
        when(watchlistRepo.findByIdAndUserId(1L, "u")).thenReturn(Optional.of(wl(1L, "u", "F")));
        when(itemRepo.deleteByWatchlistIdAndSymbol(1L, "THYAO")).thenReturn(1L);
        service.removeFromWatchlist("u", 1L, "THYAO");
        verify(itemRepo).deleteByWatchlistIdAndSymbol(1L, "THYAO");
    }
}
