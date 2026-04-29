package de.iani.playerUUIDCache.core;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class NameHistoryData {
    public static class NameChange implements Comparable<NameChange> {
        private final String newName;
        private final long date;

        public NameChange(String newName, long date) {
            this.newName = Objects.requireNonNull(newName, "newName");
            this.date = date;
        }

        public String getNewName() {
            return newName;
        }

        public long getDate() {
            return date;
        }

        @Override
        public int hashCode() {
            int result = newName.hashCode();
            result += 31 * Long.hashCode(date);
            return result;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof NameChange onc)) {
                return false;
            }
            return newName.equals(onc.newName) && date == onc.date;
        }

        @Override
        public int compareTo(NameChange other) {
            if (other == null) {
                throw new NullPointerException();
            }
            return Long.compare(date, other.date);
        }
    }

    private final UUID uuid;
    private final String firstName;
    private final List<NameChange> changes;
    private final long cacheLoadTime;

    public NameHistoryData(UUID uuid, String firstName, List<NameChange> changes, long cacheLoadTime) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(changes, "changes");
        ArrayList<NameChange> copy = new ArrayList<>(changes);
        copy.sort(null);
        this.changes = Collections.unmodifiableList(copy);
        this.cacheLoadTime = cacheLoadTime;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getFirstName() {
        return firstName;
    }

    public List<NameChange> getNameChanges() {
        return changes;
    }

    public String getName(long date) {
        NameChange change = getLastChange(date);
        return change == null ? firstName : change.getNewName();
    }

    public String getName(Date date) {
        return getName(date.getTime());
    }

    public long getCacheLoadTime() {
        return cacheLoadTime;
    }

    private NameChange getLastChange(long date) {
        if (changes.isEmpty() || changes.get(0).getDate() > date) {
            return null;
        }

        int upper = changes.size();
        int lower = 0;
        while (upper - lower > 1) {
            int index = (upper - lower) / 2 + lower;
            if (changes.get(index).getDate() <= date) {
                lower = index;
            } else {
                upper = index;
            }
        }
        return changes.get(lower);
    }
}
