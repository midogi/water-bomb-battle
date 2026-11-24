package common.protocol;

import common.Constants;
import common.model.ItemType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 바닥 아이템과 수신자 자신의 보유 수량·시야 효과를 담는다. */
public record ItemState(List<GroundItem> ground, Map<ItemType, Integer> inventory,
        boolean invisible, boolean lensActive) {
    public record GroundItem(int col, int row, ItemType type) {
        public GroundItem {
            if (col < 0 || col >= Constants.MAP_COLUMNS || row < 0 || row >= Constants.MAP_ROWS) {
                throw new IllegalArgumentException("Item is outside the map");
            }
        }
    }

    public ItemState {
        ground = List.copyOf(ground);
        inventory = Map.copyOf(inventory);
    }

    public String toWire() {
        String dropped = ground.stream().map(item -> item.col() + ":" + item.row() + ":" + item.type())
                .collect(Collectors.joining(","));
        String held = inventory.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue()).collect(Collectors.joining(","));
        return dropped + "|" + held + "|" + invisible + "|" + lensActive;
    }

    public static ItemState parse(String body) {
        String[] fields = body.split("[|]", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException("Expected ground items, inventory and two effect flags");
        }
        List<GroundItem> ground = new ArrayList<>();
        if (!fields[0].isEmpty()) {
            for (String entry : fields[0].split(",", -1)) {
                String[] item = entry.split(":", -1);
                if (item.length != 3) throw new IllegalArgumentException("Invalid ground item");
                ground.add(new GroundItem(Integer.parseInt(item[0]), Integer.parseInt(item[1]),
                        ItemType.valueOf(item[2])));
            }
        }
        Map<ItemType, Integer> inventory = new EnumMap<>(ItemType.class);
        if (!fields[1].isEmpty()) {
            for (String entry : fields[1].split(",", -1)) {
                String[] item = entry.split(":", -1);
                if (item.length != 2) throw new IllegalArgumentException("Invalid inventory entry");
                int count = Integer.parseInt(item[1]);
                if (count <= 0 || inventory.put(ItemType.valueOf(item[0]), count) != null) {
                    throw new IllegalArgumentException("Invalid inventory count or duplicate item");
                }
            }
        }
        return new ItemState(ground, inventory, readFlag(fields[2]), readFlag(fields[3]));
    }

    private static boolean readFlag(String flag) {
        return switch (flag) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Invalid item effect flag");
        };
    }
}
