package ru.fstick.registry_service.dto.service;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;



@Data
public class FileRequest {
    @NotBlank
    private String fileName;
    @NotBlank
    private String type;
}