package dev.fordes.adfs.rule.model;

public enum AdblockResourceType {
    SCRIPT("script"),
    IMAGE("image"),
    STYLESHEET("stylesheet"),
    FONT("font"),
    MEDIA("media"),
    OBJECT("object"),
    XMLHTTPREQUEST("xmlhttprequest"),
    SUBDOCUMENT("subdocument"),
    DOCUMENT("document"),
    ALL("all"),
    ELEMHIDE("elemhide"),
    GENERICHIDE("generichide"),
    GENERICBLOCK("genericblock"),
    SPECIFICHIDE("specifichide"),
    INLINE_SCRIPT("inline-script"),
    INLINE_FONT("inline-font"),
    WEBRTC("webrtc"),
    POPUP("popup"),
    WEBSOCKET("websocket"),
    PING("ping"),
    OTHER("other");

    private final String value;

    AdblockResourceType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
