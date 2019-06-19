package de.iani.playerUUIDCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class NameHistory {

    public static class NameChange implements Comparable<NameChange> {

        private String newName;
        private long date;

        public NameChange(String newName, long date) {
            com.google.common.base.Preconditions.checkNotNull(newName);
            this.newName = newName;
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
            if (!(other instanceof NameChange)) {
                return false;
            }

            NameChange onc = (NameChange) other;
            return this.newName.equals(onc.newName) && this.date == onc.date;
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

    private long cacheLoadTime;

    public NameHistory(UUID uuid, String firstName, List<NameChange> changes, long cacheLoadTime) {
        com.google.common.base.Preconditions.checkNotNull(uuid);
        com.google.common.base.Preconditions.checkNotNull(firstName);
        com.google.common.base.Preconditions.checkNotNull(changes);

        this.uuid = uuid;
        this.firstName = firstName;
        changes.sort(null);
        this.changes = Collections.unmodifiableList(new ArrayList<>(changes));

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
        if (change == null) {
            return this.firstName;
        }
        return change.getNewName();
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

    long getCacheLoadTime() {
        return cacheLoadTime;
    }

}
