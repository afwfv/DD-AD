package org.fordes.adfs.constant;

import java.util.regex.Pattern;

/**
 * @author fordes on 2024/4/9
 */
public class RegConstants {

    public static final Pattern PATTERN_PATH_ABSOLUTE = Pattern.compile("^[a-zA-Z]:([/\\\\].*)?");
    public static Pattern PATTERN_IP = Pattern.compile("((?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d))");
    public static Pattern PATTERN_DOMAIN = Pattern.compile("(?=^.{3,255}$)[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+$");
    public static Pattern DOMAIN_PART = Pattern.compile("^([-a-zA-Z0-9]{0,62})+$");
}