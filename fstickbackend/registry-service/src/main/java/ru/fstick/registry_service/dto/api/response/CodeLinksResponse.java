package ru.fstick.registry_service.dto.api.response;


import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.service.FileDownloadData;

import java.util.List;

@Data
@Builder
public class CodeLinksResponse {
    private List<FileDownloadData> files;
}
