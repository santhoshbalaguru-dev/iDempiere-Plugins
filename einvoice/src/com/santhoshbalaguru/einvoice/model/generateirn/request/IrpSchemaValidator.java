package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Validates an {@link InvoiceRequest} against the constraints declared with
 * {@link IrpField} (the official IRP JSON schema). Walks nested objects and lists.
 * Returns readable messages such as "SellerDtls.Loc: length 2 is below minimum 3".
 */
public final class IrpSchemaValidator {

    private static final String MODEL_PACKAGE = InvoiceRequest.class.getPackage().getName();

    private IrpSchemaValidator() {
    }

    public static List<String> validate(Object root) {
        List<String> errors = new ArrayList<>();
        if (root == null) {
            errors.add("Request is null");
            return errors;
        }
        walk(root, "", errors);
        return errors;
    }

    private static void walk(Object bean, String path, List<String> errors) {
        for (Field f : bean.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()))
                continue;
            f.setAccessible(true);
            Object value;
            try {
                value = f.get(bean);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }

            String name = jsonName(f);
            String here = path.isEmpty() ? name : path + "." + name;

            IrpField rule = f.getAnnotation(IrpField.class);
            if (rule != null)
                check(rule, value, here, errors);

            if (value == null)
                continue;

            if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (rule != null && rule.required() && list.isEmpty())
                    errors.add(here + ": must contain at least one entry");
                if (rule != null && rule.maxItems() >= 0 && list.size() > rule.maxItems())
                    errors.add(here + ": has " + list.size() + " entries, the maximum is " + rule.maxItems());
                for (int i = 0; i < list.size(); i++) {
                    Object el = list.get(i);
                    if (el != null && isModel(el))
                        walk(el, here + "[" + i + "]", errors);
                }
            } else if (isModel(value)) {
                walk(value, here, errors);
            }
        }
    }

    private static void check(IrpField r, Object value, String path, List<String> errors) {
        if (value == null) {
            if (r.required())
                errors.add(path + ": is required");
            return;
        }

        if (value instanceof String) {
            String s = (String) value;
            if (r.min() >= 0 && s.length() < r.min())
                errors.add(path + ": length " + s.length() + " is below minimum " + r.min() + " (value '" + s + "')");
            if (r.max() >= 0 && s.length() > r.max())
                errors.add(path + ": length " + s.length() + " exceeds maximum " + r.max());
            if (r.allowed().length > 0 && !Arrays.asList(r.allowed()).contains(s))
                errors.add(path + ": '" + s + "' is not one of " + Arrays.toString(r.allowed()));
            if (!r.pattern().isEmpty() && !Pattern.compile(r.pattern()).matcher(s).find())
                errors.add(path + ": '" + shorten(s) + "' does not match the required format " + r.pattern());
        } else if (value instanceof Number) {
            BigDecimal n = new BigDecimal(value.toString());
            if (!r.minVal().isEmpty() && n.compareTo(new BigDecimal(r.minVal())) < 0)
                errors.add(path + ": " + n.toPlainString() + " is below minimum " + r.minVal());
            if (!r.maxVal().isEmpty() && n.compareTo(new BigDecimal(r.maxVal())) > 0)
                errors.add(path + ": " + n.toPlainString() + " exceeds maximum " + r.maxVal());
        }
    }

    private static boolean isModel(Object o) {
        return o.getClass().getName().startsWith(MODEL_PACKAGE);
    }

    private static String jsonName(Field f) {
        JsonProperty p = f.getAnnotation(JsonProperty.class);
        return p != null ? p.value() : f.getName();
    }

    private static String shorten(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }
}
