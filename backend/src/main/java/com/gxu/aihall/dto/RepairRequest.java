package com.gxu.aihall.dto;

import lombok.Data;
import java.util.List;

@Data
public class RepairRequest {
    private String faultType;
    private String location;
    private String description;
    private List<String> imageUrls;
    private String contactName;
    private String contactPhone;
}
