package com.santhoshbalaguru.einvoice.model.generateirn.request;

/** Regex patterns and numeric limits taken verbatim from the official IRP schema. */
public final class IrpPatterns {

    private IrpPatterns() {
    }

    /** Free text: no double quote and no backslash (IRP validation rule 4). */
    public static final String NO_QUOTE = "^[^\"\\\\]*$";
    /** dd/MM/yyyy, years 2010-2029 exactly as the schema defines it. */
    public static final String DATE = "^[0-3][0-9]/[0-1][0-9]/[2][0][1-2][0-9]$";

    public static final String GSTIN = "^([0-9]{2}[0-9A-Z]{13})$";
    public static final String GSTIN_OR_URP = "^(([0-9]{2}[0-9A-Z]{13})|URP)$";
    public static final String STATE_CODE = "^(?!0+$)([0-9]{1,2})$";
    public static final String PHONE = "^([0-9]{6,12})$";
    public static final String EMAIL = "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$";

    public static final String DOC_NO = "^([a-zA-Z1-9]{1}[a-zA-Z0-9/-]{0,15})$";
    public static final String PREC_INV_NO = "^[1-9a-zA-Z]{1}[0-9a-zA-Z/-]{1,15}$";
    public static final String SL_NO = "^([0-9]{1,6})$";
    public static final String HSN = "^(?!0+$)([0-9]{4}|[0-9]{6}|[0-9]{8})$";
    public static final String UNIT = "^([A-Z|a-z]{3,8})$";
    public static final String COUNTRY_2 = "^([A-Z|a-z]{2})$";
    public static final String YN = "^([Y|N]{1})$";
    public static final String PORT = "^[0-9|A-Z|a-z]{2,10}$";
    public static final String FOR_CUR = "^[A-Z|a-z]{3,16}$";
    public static final String CNT_CODE = "^([A-Z]{2})$";
    public static final String TRANS_DOC_NO = "^([a-zA-Z0-9/-]{1,15})$";
    public static final String TRANS_DOC_DT = "^[a-zA-Z0-9]{1}[a-zA-Z0-9-/]*$";
    public static final String VEH_NO = "^([A-Z|a-z|0-9]{4,20})$";

    public static final String PIN_MIN = "100000";
    public static final String PIN_MAX = "999999";
    public static final String AMT_MAX = "999999999999.99";
    public static final String RATE_MAX = "999.999";
    public static final String QTY_MAX = "9999999999.999";
    public static final String PRICE_MAX = "999999999999.999";
    public static final String TOT_MAX = "99999999999999.99";
}
