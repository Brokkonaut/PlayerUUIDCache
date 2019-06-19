package de.iani.playerUUIDCache;

import java.sql.Date;
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
        
        /**
         * Returns the name the player adopted with this change.
         * 
         * @return new name
         */
        public String getNewName() {
            return this.newName;
        }
        
        /**
         * Returns the date at which this change was performed, as a unix epoch timestamp in ms.
         * 
         * @return date of the change
         */
        public long getDate() {
            return this.date;
        }
        
        @Override
        public int hashCode() {
            int result = this.newName.hashCode();
            result += 31 * Long.hashCode(this.date);
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
            
            return Long.compare(this.date, other.date);
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
    
    /**
     * Returns the uuid of the player with this history.
     * 
     * @return player's uuid
     */
    public UUID getUUID() {
        return this.uuid;
    }
    
    /**
     * Returns the first name the player with this history ever had.
     * 
     * @return first name of the player
     */
    public String getFirstName() {
        return this.firstName;
    }
    
    /**
     * Returns a list of all name changes the player with this history had, in chronological order.
     * 
     * @return list of name changes
     */
    public List<NameChange> getNameChanges() {
        return this.changes;
    }
    
    /**
     * Returns the name the player with this history had at the given date.
     * 
     * Note that if this history doesn't contain any changes or if the given date lies before the
     * first change, the players first name is returned (even if the account didn't exist then). For
     * all dates lying in the future, the current name is returned. If the given date matches that
     * of one change exactly, that changes new name is returned.
     * 
     * @param date a date as unix epoch timestamp in ms
     * @return the name the player with this history had at the given date
     */
    public String getName(long date) {
        NameChange change = getLastChange(date);
        if (change == null) {
            return this.firstName;
        }
        return change.getNewName();
    }
    
    /**
     * Returns the name the player with this history had at the given date.
     * 
     * Note that if this history doesn't contain any changes or if the given date lies before the
     * first change, the players first name is returned (even if the account didn't exist then). For
     * all dates lying in the future, the current name is returned. If the given date matches that
     * of one change exactly, that changes new name is returned.
     * 
     * @param date a date
     * @return the name the player with this history had at the given date
     */
    public String getName(Date date) {
        return getName(date.getTime());
    }
    
    private NameChange getLastChange(long date) {
        if (this.changes.isEmpty() || this.changes.get(0).getDate() > date) {
            return null;
        }
        
        int upper = this.changes.size();
        int lower = 0;
        while (upper - lower > 1) {
            int index = (upper - lower) / 2 + lower;
            if (this.changes.get(index).getDate() <= date) {
                lower = index;
            } else {
                upper = index;
            }
        }
        
        return this.changes.get(lower);
    }
    
    long getCacheLoadTime() {
        return this.cacheLoadTime;
    }
    
}
