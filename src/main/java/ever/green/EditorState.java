package ever.green.utils;

public enum EditorState {

    EMPTY_PAGE,

    // Armor Stand Context
    ARMOR_STAND_PAGE_1, // Placement & Scale
    ARMOR_STAND_PAGE_2, // Anatomy & Posing
    ARMOR_STAND_PAGE_3, // Properties, Equipment, Names
    ARMOR_STAND_PAGE_4, // Clipboards, Visibility, Reset

    // Living Entity Context
    MOB_PAGE_1,
    MOB_PAGE_2;

    public EditorState getNextPage() {
        return switch (this) {
            case EMPTY_PAGE -> EMPTY_PAGE;
            case ARMOR_STAND_PAGE_1 -> ARMOR_STAND_PAGE_2;
            case ARMOR_STAND_PAGE_2 -> ARMOR_STAND_PAGE_3;
            case ARMOR_STAND_PAGE_3 -> ARMOR_STAND_PAGE_4;
            case ARMOR_STAND_PAGE_4 -> ARMOR_STAND_PAGE_1;
            case MOB_PAGE_1 -> MOB_PAGE_2;
            case MOB_PAGE_2 -> MOB_PAGE_1;
        };
    }

    public EditorState getPreviousPage() {
        return switch (this) {
            case EMPTY_PAGE -> EMPTY_PAGE;
            case ARMOR_STAND_PAGE_1 -> ARMOR_STAND_PAGE_4;
            case ARMOR_STAND_PAGE_2 -> ARMOR_STAND_PAGE_1;
            case ARMOR_STAND_PAGE_3 -> ARMOR_STAND_PAGE_2;
            case ARMOR_STAND_PAGE_4 -> ARMOR_STAND_PAGE_3;
            case MOB_PAGE_1 -> MOB_PAGE_2;
            case MOB_PAGE_2 -> MOB_PAGE_1;
        };
    }
}