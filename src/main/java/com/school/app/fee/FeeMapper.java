package com.school.app.fee;

import org.springframework.stereotype.Component;

@Component
public class FeeMapper {

    public FeeDto toDto(Fee fee) {
        return new FeeDto(
                fee.getId(),
                fee.getStudent().getId(),
                fee.getTerm(),
                fee.getAmountDue(),
                fee.getAmountPaid(),
                fee.getStatus(),
                fee.getDueDate()
        );
    }
}
