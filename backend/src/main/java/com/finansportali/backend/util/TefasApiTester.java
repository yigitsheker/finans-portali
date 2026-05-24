package com.finansportali.backend.util;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * TEFAS API endpoint'lerini test etmek için yardımcı sınıf.
 * 
 * Kullanım:
 * 1. Bu dosyayı çalıştırın (main method)
 * 2. Hangi endpoint'lerin çalıştığını görün
 * 3. Çalışan endpoint'leri TefasFundFetcher.java'ya ekleyin
 */
public class TefasApiTester {

    private static final Logger log = LoggerFactory.getLogger(TefasApiTester.class);
    private static final RestTemplate restTemplate = new RestTemplate();
    
    // Test edilecek base URL'ler
    private static final List<String> BASE_URLS = Arrays.asList(
        "https://www.tefas.gov.tr/api/DB",
        "https://www.tefas.gov.tr/api",
        "https://tefas.gov.tr/api/DB",
        "https://tefas.gov.tr/api"
    );
    
    // Test edilecek endpoint'ler
    private static final List<String> ENDPOINTS = Arrays.asList(
        "/BindComparisonFundList",
        "/BindHistoryInfo",
        "/BindHistoryAllocation",
        "/TarihselVeriler",
        "/FonKarsilastirma",
        "/FonListesi",
        "/FonBilgileri"
    );
    
    public static void main(String[] args) {
        log.info("=== TEFAS API Endpoint Tester ===");
        log.info("Testing {} base URLs with {} endpoints", BASE_URLS.size(), ENDPOINTS.size());
        
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dateStrTR = today.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        
        // Test edilecek fon kodu
        String testFundCode = "AAK"; // Akbank A Tipi Fon
        
        int successCount = 0;
        int failCount = 0;
        
        // Her base URL ve endpoint kombinasyonunu test et
        for (String baseUrl : BASE_URLS) {
            for (String endpoint : ENDPOINTS) {
                String fullUrl = baseUrl + endpoint;
                
                // Farklı parametre kombinasyonlarını test et
                List<String> urlVariants = Arrays.asList(
                    fullUrl,
                    fullUrl + "?fontip=YAT",
                    fullUrl + "?fonkod=" + testFundCode,
                    fullUrl + "?fontip=YAT&fonkod=" + testFundCode,
                    fullUrl + "?fontip=YAT&fonkod=" + testFundCode + "&bastarih=" + dateStr + "&bittarih=" + dateStr,
                    fullUrl + "?fontip=YAT&fonkod=" + testFundCode + "&bastarih=" + dateStrTR + "&bittarih=" + dateStrTR
                );
                
                for (String url : urlVariants) {
                    try {
                        log.info("Testing: {}", url);
                        
                        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                        
                        if (response.getStatusCode().is2xxSuccessful()) {
                            String body = response.getBody();
                            if (body != null && !body.isEmpty() && !body.contains("ERR-")) {
                                log.info("✅ SUCCESS: {} - Response length: {}", url, body.length());
                                log.info("   Response preview: {}", body.substring(0, Math.min(200, body.length())));
                                successCount++;
                            } else {
                                log.warn("⚠️  EMPTY/ERROR: {} - {}", url, body != null ? body.substring(0, Math.min(100, body.length())) : "null");
                                failCount++;
                            }
                        } else {
                            log.warn("❌ FAILED: {} - Status: {}", url, response.getStatusCode());
                            failCount++;
                        }
                        
                        // Rate limiting - API'yi yormamak için
                        Thread.sleep(500);

                    } catch (InterruptedException ie) {
                        // Preserve interrupt status (Sonar S2142) and bail out of
                        // the test loop — a cancelled diagnostic run shouldn't
                        // keep hammering TEFAS.
                        Thread.currentThread().interrupt();
                        log.warn("⚠️ TEFAS test interrupted at: {}", url);
                        return;
                    } catch (Exception e) {
                        log.error("❌ ERROR: {} - {}", url, e.getMessage());
                        failCount++;
                    }
                }
            }
        }
        
        log.info("=== Test Results ===");
        log.info("✅ Successful: {}", successCount);
        log.info("❌ Failed: {}", failCount);
        log.info("Total tested: {}", successCount + failCount);
    }
}
