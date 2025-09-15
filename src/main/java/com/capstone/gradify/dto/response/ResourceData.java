package com.capstone.gradify.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceData {
    @JsonProperty("@odata.type")
    private String odataType;

    @JsonProperty("@odata.id")
    private String odataId;

    private String id;
}
