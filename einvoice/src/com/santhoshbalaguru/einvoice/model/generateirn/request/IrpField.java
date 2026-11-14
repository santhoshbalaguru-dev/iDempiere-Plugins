package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constraints copied from the official IRP invoice JSON schema (GST-India Invoice Document).
 * Checked by {@link IrpSchemaValidator}; Jackson ignores this annotation.
 * <ul>
 * <li>min / max  - string length (-1 = not set)</li>
 * <li>minVal / maxVal - numeric bounds, as decimal strings ("" = not set)</li>
 * <li>pattern - regex, applied with find() like JSON Schema "pattern"</li>
 * <li>allowed - enum values</li>
 * <li>required - listed in the schema's "required" array</li>
 * <li>maxItems - maximum number of entries in a list</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IrpField {
    int min() default -1;
    int max() default -1;
    String minVal() default "";
    String maxVal() default "";
    String pattern() default "";
    String[] allowed() default {};
    boolean required() default false;
    int maxItems() default -1;   // list size limit (-1 = not set)
}
