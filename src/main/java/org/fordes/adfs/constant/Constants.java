package org.fordes.adfs.constant;

import java.io.File;
import java.util.Set;

public class Constants {

    public static final String ROOT_PATH = System.getProperty("user.dir");
    public static final String FILE_SEPARATOR = File.separator;

    public static final String HEADER_DATE = "${date}";
    public static final String HEADER_NAME = "${name}";
    public static final String HEADER_TOTAL = "${total}";
    public static final String HEADER_TYPE = "${type}";
    public static final String HEADER_DESC = "${desc}";

    public static final String EMPTY = "";
    public static final String DOT = ".";
    public static final String EXCLAMATION = "!";
    public static final String HASH = "#";
    public static final String AT = "@";
    public static final String PERCENT = "%";
    public static final String DOLLAR = "$";
    public static final String UNDERLINE = "_";
    public static final String DASH = "-";
    public static final String TILDE = "~";
    public static final String COMMA = ",";
    public static final String SLASH = "/";
    public static final String LEFT_BRACKETS = "[";
    public static final String RIGHT_BRACKETS = "]";
    public static final String OR = "||";
    public static final String ASTERISK = "*";
    public static final String QUESTION_MARK = "?";
    public static final String A = "a";
    public static final String CARET = "^";
    public static final String WHITESPACE = " ";
    public static final String CR = "\r";
    public static final String LF = "\n";
    public static final String CRLF = CR + LF;
    public static final String QUOTE = "\"";
    public static final String SINGLE_QUOTE = "'";
    public static final String ADD = "+";
    public static final String COLON = ":";
    public static final String EQUAL = "=";


    public static final Set<String> LOCAL_IP = Set.of("0.0.0.0", "127.0.0.1", "::1");
    public static final Set<String> LOCAL_DOMAIN = Set.of("localhost", "localhost.localdomain", "local", "ip6-localhost", "ip6-loopback");
    public static final String LOCAL_V4 = "127.0.0.1";
    public static final String UNKNOWN_IP = "0.0.0.0";
    public static final String LOCAL_V6 = "::1";
    public static final String LOCALHOST = "localhost";
    public static final String DOUBLE_AT = "@@";
    public static final String IMPORTANT = "important";
    public static final String DOMAIN = "domain";
    public static final String TAB = "\t";
    public static final String PAYLOAD = "payload";

    public static final String DNSMASQ_HEADER = "address=/";
    public static final String SMARTDNS_HEADER = "address /";
}
