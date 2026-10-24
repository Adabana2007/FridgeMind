package com.fridgemind.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 跨字段日期校验：after 字段必须在 before 字段之后（或同时可空，不校验）
 */
@Documented
@Constraint(validatedBy = DateAfterValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DateAfter {
    String message() default "结束日期必须在开始日期之后";
    String before();
    String after();
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
