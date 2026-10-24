package com.fridgemind.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

import java.time.LocalDate;

public class DateAfterValidator implements ConstraintValidator<DateAfter, Object> {

    private String beforeField;
    private String afterField;
    private String message;

    @Override
    public void initialize(DateAfter annotation) {
        this.beforeField = annotation.before();
        this.afterField = annotation.after();
        this.message = annotation.message();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
        Object before = wrapper.getPropertyValue(beforeField);
        Object after = wrapper.getPropertyValue(afterField);

        // 任一为空时不校验
        if (before == null || after == null) return true;

        try {
            LocalDate beforeDate = toLocalDate(before);
            LocalDate afterDate = toLocalDate(after);
            if (!afterDate.isAfter(beforeDate)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(message)
                        .addPropertyNode(afterField)
                        .addConstraintViolation();
                return false;
            }
            return true;
        } catch (Exception e) {
            return true; // 格式异常由其他校验处理
        }
    }

    private LocalDate toLocalDate(Object val) {
        if (val instanceof LocalDate d) return d;
        return LocalDate.parse(val.toString());
    }
}
