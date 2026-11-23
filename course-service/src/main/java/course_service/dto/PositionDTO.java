package course_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PositionDTO {
    @NotNull @DecimalMin("-90") @DecimalMax("90")
    private BigDecimal lat;

    @NotNull @DecimalMin("-180") @DecimalMax("180")
    private BigDecimal lng;
}
