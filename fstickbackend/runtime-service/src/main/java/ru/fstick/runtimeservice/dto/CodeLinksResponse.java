package ru.fstick.runtimeservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CodeLinksResponse {
    private List<FileDownloadData> files;
}