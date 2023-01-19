package com.reesen.Reesen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DriveAssessmentDTO {

    @NotNull(message = "{required}")
    private Set<RouteDTO> locations;
    @NotNull(message = "{required}")
    private VehicleTypeDTO vehicleType;
    @NotNull(message = "{required}")
    private boolean babyTransport;
    @NotNull(message = "{required}")
    private boolean petTransport;

}
