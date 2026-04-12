package ru.fstick.registry_service.dto.service;


import lombok.Data;



@Data
public class FileRequest {
    private String fileName;
    private String type;
}