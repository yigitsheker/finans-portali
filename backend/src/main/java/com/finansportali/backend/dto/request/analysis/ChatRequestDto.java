package com.finansportali.backend.dto.request.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Inbound chat message from the Analysis page chatbot. */
public class ChatRequestDto {
    @NotBlank
    @Size(max = 2000)
    private String message;

    /** Optional locale hint (defaults to "tr"). */
    private String lang;

    public ChatRequestDto() {}

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
}
