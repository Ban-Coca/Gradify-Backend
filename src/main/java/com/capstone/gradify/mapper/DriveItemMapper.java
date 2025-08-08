package com.capstone.gradify.mapper;

import com.capstone.gradify.dto.response.DriveItemResponse;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface DriveItemMapper {
    @Mapping(target = "isFolder", expression = "java(item.getFolder() != null)")
    @Mapping(target = "isFile", expression = "java(item.getFile() != null)")
    @Mapping(target = "fileType", expression = "java(getFileType(item))")
    @Mapping(target = "lastModifiedDateTime",
            expression = "java(item.getLastModifiedDateTime() != null ? item.getLastModifiedDateTime().toString() : null)")
    DriveItemResponse toDTO(DriveItem item);

    default String getFileType(DriveItem item) {
        if (item.getFolder() != null) return "folder";
        if (item.getName() == null) return "unknown";

        String name = item.getName().toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls") ||
                name.endsWith(".xlsm") || name.endsWith(".xlsb")) {
            return "excel";
        }

        return "file";
    }

    default List<DriveItemResponse> toDTO(DriveItemCollectionResponse response) {
        if (response == null || response.getValue() == null) {
            return Collections.emptyList();
        }
        return response.getValue().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
