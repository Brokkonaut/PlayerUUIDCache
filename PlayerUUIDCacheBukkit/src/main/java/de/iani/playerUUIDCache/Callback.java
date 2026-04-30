package de.iani.playerUUIDCache;

import de.iani.playerUUIDCache.core.CoreCallback;

public interface Callback<T> extends CoreCallback<T> {
    @Override
    public void onComplete(T t);
}
