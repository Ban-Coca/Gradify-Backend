package com.capstone.gradify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DriveItemResponse {
    private String id;
    private String name;
    private String webUrl;
    private Long size;
    private String lastModifiedDateTime;
    private Boolean isFolder;
    private Boolean isFile;
    private String fileType;
}
