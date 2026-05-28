package ru.fstick.runtimeservice.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
public class FileDownloadData {
    private String download_url;
}