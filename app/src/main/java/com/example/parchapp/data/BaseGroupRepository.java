package com.example.parchapp.data;

import com.example.parchapp.domain.model.Group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Observer bookkeeping shared by every group repository. Subclasses start one data source
    subscription per group when the first screen observes it, stop it when the last one leaves,
    and call {@link #notifyListeners(Group)} when the group changes. Main thread only.
 */
abstract class BaseGroupRepository implements GroupRepository {

    private final Map<String, List<GroupListener>> listenersByGroup = new HashMap<>();
    private final Map<String, Group> latestByGroup = new HashMap<>();

    protected abstract void startObserving(String groupId);

    protected abstract void stopObserving(String groupId);

    @Override
    public final void observeGroup(String groupId, GroupListener listener) {
        List<GroupListener> listeners = listenersByGroup.get(groupId);
        boolean first = listeners == null;
        if (first) {
            listeners = new ArrayList<>();
            listenersByGroup.put(groupId, listeners);
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        if (first) {
            startObserving(groupId);
        } else if (latestByGroup.containsKey(groupId)) {
            listener.onGroupChanged(latestByGroup.get(groupId));
        }
    }

    @Override
    public final void removeGroupListener(String groupId, GroupListener listener) {
        List<GroupListener> listeners = listenersByGroup.get(groupId);
        if (listeners == null) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            listenersByGroup.remove(groupId);
            latestByGroup.remove(groupId);
            stopObserving(groupId);
        }
    }

    protected final void notifyListeners(Group group) {
        List<GroupListener> listeners = listenersByGroup.get(group.getId());
        if (listeners == null) {
            return;
        }
        latestByGroup.put(group.getId(), group);
        for (GroupListener listener : new ArrayList<>(listeners)) {
            listener.onGroupChanged(group);
        }
    }
}
