package com.gxu.aihall.dto;

import lombok.Data;
import java.util.List;

@Data
public class ApplicationRequest {
    private String type;
    private String title;
    private String formData;
    private List<String> materialUrls;
}
