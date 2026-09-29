package ru.fstick.integrationservice.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ResolveBatchRequest {
    private List<String> mxids;
}
