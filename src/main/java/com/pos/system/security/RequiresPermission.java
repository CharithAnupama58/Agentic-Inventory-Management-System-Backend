package com.pos.system.security;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {
    String value();           // permission constant e.g. "PRODUCT_DELETE"
    String message() default "You do not have permission to perform this action";
}
