package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.admin.KeycloakUserDto;
import com.finansportali.backend.entity.NewsFeed;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.NewsArticleRepository;
import com.finansportali.backend.repository.NewsFeedRepository;
import com.finansportali.backend.service.InvestmentFundService;
import com.finansportali.backend.service.KeycloakAdminService;
import com.finansportali.backend.service.MarketService;
import com.finansportali.backend.service.NewsService;
import com.finansportali.backend.service.UserService;
import com.finansportali.backend.service.scheduler.PriceRefreshScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser(roles = "ADMIN")
class AdminControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private MarketCandleRepository candleRepo;
    @MockitoBean private MarketQuoteRepository quoteRepo;
    @MockitoBean private MarketInstrumentRepository instrumentRepo;
    @MockitoBean private MarketService marketService;
    @MockitoBean private PriceRefreshScheduler scheduler;
    @MockitoBean private NewsArticleRepository newsRepo;
    @MockitoBean private NewsService newsService;
    @MockitoBean private UserService userService;
    @MockitoBean private KeycloakAdminService keycloakAdminService;
    @MockitoBean private InvestmentFundService investmentFundService;
    @MockitoBean private NewsFeedRepository feedRepo;

    private NewsFeed feed(Long ignored, String url) {
        // NewsFeed has no setter for id (auto-generated). The id comes
        // back via repo.save() in production; for tests we just stub the
        // repo lookup directly.
        NewsFeed f = new NewsFeed(url, "hisse", "Source A");
        f.setEnabled(true);
        return f;
    }

    @Test
    void list_feeds_maps_entities_to_dtos() throws Exception {
        when(feedRepo.findAllByOrderByCategoryAscSourceAsc())
                .thenReturn(List.of(feed(1L, "https://x.example/rss")));

        mvc.perform(get("/api/v1/admin/feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("https://x.example/rss"));
    }

    @Test
    void add_feed_rejects_blank_url() throws Exception {
        mvc.perform(post("/api/v1/admin/feeds").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void add_feed_rejects_duplicate_url() throws Exception {
        when(feedRepo.findByUrl("https://x.example/rss"))
                .thenReturn(Optional.of(feed(2L, "https://x.example/rss")));

        mvc.perform(post("/api/v1/admin/feeds").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://x.example/rss\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void add_feed_saves_and_returns_dto() throws Exception {
        when(feedRepo.findByUrl(anyString())).thenReturn(Optional.empty());
        // NewsFeed has no public id setter; return the saved instance as-is.
        // The id assertion below would be null, so we assert the defaults
        // applied by the controller (blank → "diger"/"Custom").
        when(feedRepo.save(any(NewsFeed.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/admin/feeds").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://new.rss\",\"category\":\"\",\"source\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("diger"))
                .andExpect(jsonPath("$.source").value("Custom"));
    }

    @Test
    void toggle_feed_flips_enabled() throws Exception {
        NewsFeed f = feed(5L, "https://x.example/rss");
        when(feedRepo.findById(5L)).thenReturn(Optional.of(f));
        when(feedRepo.save(any(NewsFeed.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/admin/feeds/5/toggle").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void toggle_feed_404_when_missing() throws Exception {
        when(feedRepo.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/admin/feeds/99/toggle").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_feed_returns_204_when_present() throws Exception {
        // The endpoint now cascades through related articles before dropping
        // the feed row, so it looks up the feed via findById (not existsById)
        // to read source + category and feed those into the article delete.
        when(feedRepo.findById(5L))
                .thenReturn(Optional.of(feed(5L, "https://x.example/rss")));
        when(newsRepo.deleteBySourceNameAndCategory(anyString(), anyString()))
                .thenReturn(0);

        mvc.perform(delete("/api/v1/admin/feeds/5").with(csrf()))
                .andExpect(status().isNoContent());

        verify(newsRepo).deleteBySourceNameAndCategory("Source A", "hisse");
        verify(feedRepo).deleteById(5L);
    }

    @Test
    void delete_feed_404_when_missing() throws Exception {
        when(feedRepo.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/v1/admin/feeds/99").with(csrf()))
                .andExpect(status().isNotFound());

        verify(newsRepo, org.mockito.Mockito.never())
                .deleteBySourceNameAndCategory(anyString(), anyString());
    }

    @Test
    void me_returns_current_admin_identity() throws Exception {
        when(userService.getCurrentUserId()).thenReturn("u1");
        when(userService.getCurrentUsername()).thenReturn("admin");
        when(userService.getCurrentUserEmail()).thenReturn("a@b.c");
        when(userService.getCurrentUserFullName()).thenReturn("Admin User");
        when(userService.getCurrentUserRoles()).thenReturn(List.of("ADMIN"));
        when(userService.isAdmin()).thenReturn(true);

        mvc.perform(get("/api/v1/admin/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.isAdmin").value(true));
    }

    @Test
    void reset_market_clears_and_reseeds() throws Exception {
        when(userService.getCurrentUsername()).thenReturn("admin");

        mvc.perform(post("/api/v1/admin/reset-market").with(csrf()))
                .andExpect(status().isOk());

        verify(candleRepo).deleteAll();
        verify(quoteRepo).deleteAll();
        verify(instrumentRepo).deleteAll();
        verify(marketService).seedIfEmpty();
    }

    @Test
    void refresh_prices_triggers_scheduler() throws Exception {
        when(userService.getCurrentUsername()).thenReturn("admin");

        mvc.perform(post("/api/v1/admin/refresh-prices").with(csrf()))
                .andExpect(status().isOk());

        verify(scheduler).refreshAll();
    }

    @Test
    void reset_news_clears_and_fetches() throws Exception {
        when(userService.getCurrentUsername()).thenReturn("admin");

        mvc.perform(post("/api/v1/admin/reset-news").with(csrf()))
                .andExpect(status().isOk());

        verify(newsRepo).deleteAll();
        verify(newsService).fetchAndSaveNews();
    }

    @Test
    void reset_funds_wipes_and_repopulates() throws Exception {
        when(investmentFundService.wipeAll()).thenReturn(15);
        when(investmentFundService.getAllFunds()).thenReturn(List.of());
        when(userService.getCurrentUsername()).thenReturn("admin");

        mvc.perform(post("/api/v1/admin/reset-funds").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wiped").value(15));

        verify(investmentFundService).updateFundPrices();
    }

    @Test
    void list_users_passes_query_params() throws Exception {
        KeycloakUserDto u = new KeycloakUserDto(
                "id-1", "ada", "ada@example.com", "Ada", "Lovelace",
                true, true, System.currentTimeMillis(), List.of(), false);
        when(keycloakAdminService.listUsers(eq("ad"), anyInt(), anyInt())).thenReturn(List.of(u));

        mvc.perform(get("/api/v1/admin/users")
                        .param("search", "ad")
                        .param("first", "0")
                        .param("max", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("ada"));
    }

    @Test
    void ban_user_calls_keycloak_admin_with_enabled_false() throws Exception {
        when(userService.getCurrentUsername()).thenReturn("admin");

        mvc.perform(post("/api/v1/admin/users/u-1/ban").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(keycloakAdminService).setUserEnabled("u-1", false);
    }

    @Test
    void unban_user_calls_keycloak_admin_with_enabled_true() throws Exception {
        when(userService.getCurrentUsername()).thenReturn("admin");

        mvc.perform(post("/api/v1/admin/users/u-1/unban").with(csrf()))
                .andExpect(status().isOk());

        verify(keycloakAdminService).setUserEnabled("u-1", true);
    }

    @Test
    void require_2fa_adds_configure_totp() throws Exception {
        when(userService.getCurrentUsername()).thenReturn("admin");

        mvc.perform(post("/api/v1/admin/users/u-1/require-2fa").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredAction").value("CONFIGURE_TOTP"));

        verify(keycloakAdminService).addRequiredAction("u-1", "CONFIGURE_TOTP");
    }

    @Test
    void reset_2fa_clears_totp() throws Exception {
        when(userService.getCurrentUsername()).thenReturn("admin");

        mvc.perform(post("/api/v1/admin/users/u-1/reset-2fa").with(csrf()))
                .andExpect(status().isOk());

        verify(keycloakAdminService).removeTotpCredentials("u-1");
    }
}
