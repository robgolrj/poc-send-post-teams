package com.example.pocsendpostteams;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageCard {

    @JsonProperty("@type")
    private final String type = "MessageCard";

    @JsonProperty("@context")
    private final String context = "http://schema.org/extensions";

    private String title;

    private String text;

    private String themeColor;

}
