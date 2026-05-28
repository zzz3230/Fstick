package ru.fstick.runtimeservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileDownloadData {
    private String downloadUrl;
}