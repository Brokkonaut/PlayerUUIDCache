package de.iani.playerUUIDCache;

import de.iani.playerUUIDCache.core.NameHistoryData;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class NameHistory {
    public static class NameChange implements Comparable<NameChange> {
        private final String newName;
        private final long date;

        public NameChange(String newName, long date) {
            this.newName = Objects.requireNonNull(newName, "newName");
            this.date = date;
        }

        /**
         * Returns the name the player adopted with this change.
         *
         * @return new name
         */
        public String getNewName() {
            return newName;
        }

        /**
         * Returns the date at which this change was performed, as a unix epoch timestamp in ms.
         *
         * @return date of the change
         */
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

    private final NameHistoryData data;
    private final List<NameChange> changes;

    public NameHistory(UUID uuid, String firstName, List<NameChange> changes, long cacheLoadTime) {
        ArrayList<NameChange> sortedChanges = new ArrayList<>(Objects.requireNonNull(changes, "changes"));
        sortedChanges.sort(null);
        this.changes = Collections.unmodifiableList(sortedChanges);
        data = new NameHistoryData(uuid, firstName, toCoreChanges(sortedChanges), cacheLoadTime);
    }

    /**
     * Returns the uuid of the player with this history.
     *
     * @return player's uuid
     */
    public UUID getUUID() {
        return data.getUUID();
    }

    /**
     * Returns the first name the player with this history ever had.
     *
     * @return first name of the player
     */
    public String getFirstName() {
        return data.getFirstName();
    }

    /**
     * Returns a list of all name changes the player with this history had, in chronological order.
     *
     * @return list of name changes
     */
    public List<NameChange> getNameChanges() {
        return changes;
    }

    /**
     * Returns the name the player with this history had at the given date.
     *
     * Note that if this history doesn't contain any changes or if the given date lies before the
     * first change, the players first name is returned (even if the account didn't exist then). For
     * all dates lying in the future, the current name is returned. If the given date matches that
     * of one change exactly, that changes new name is returned.
     *
     * @param date
     *            a date as unix epoch timestamp in ms
     * @return the name the player with this history had at the given date
     */
    public String getName(long date) {
        return data.getName(date);
    }

    /**
     * Returns the name the player with this history had at the given date.
     *
     * Note that if this history doesn't contain any changes or if the given date lies before the
     * first change, the players first name is returned (even if the account didn't exist then). For
     * all dates lying in the future, the current name is returned. If the given date matches that
     * of one change exactly, that changes new name is returned.
     *
     * @param date
     *            a date
     * @return the name the player with this history had at the given date
     */
    public String getName(Date date) {
        return data.getName(date);
    }

    long getCacheLoadTime() {
        return data.getCacheLoadTime();
    }

    NameHistoryData toData() {
        return data;
    }

    private static ArrayList<NameHistoryData.NameChange> toCoreChanges(List<NameChange> changes) {
        ArrayList<NameHistoryData.NameChange> result = new ArrayList<>();
        for (NameChange change : changes) {
            result.add(new NameHistoryData.NameChange(change.getNewName(), change.getDate()));
        }
        return result;
    }
}
