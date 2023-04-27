package oam.security.controller.dto;


import lombok.Data;

@Data
public class LimitInputDto {
    private Long uploadMaxBandwidth;
    private Long downloadMaxBandwidth;
    private Long uploadMinBandwidth;
    private Long downloadMinBandwidth;
}
