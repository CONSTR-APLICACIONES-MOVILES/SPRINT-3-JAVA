package com.example.parchapp.data;

import com.example.parchapp.domain.model.ChatMessage;

import java.util.List;

/* The messages of a group chat (House Chat) or of a chat between two people. */
public interface ChatRepository {

    interface MessagesListener {
        /* the whole conversation, oldest first, with the date dividers; fromCache means it came from the phone */
        void onMessages(List<ChatMessage> messages, boolean fromCache);

        void onFailed(Exception error);
    }

    interface Registration {
        void remove();
    }

    /* a chat: the House Chat of a group when friendId is null, otherwise the chat between me and friendId */
    final class ChatRef {
        final String groupId;
        final String friendId;

        public ChatRef(String groupId, String friendId) {
            this.groupId = groupId;
            this.friendId = friendId;
        }

        public boolean isDirect() {
            return friendId != null;
        }
    }

    /* listens in real time until remove() is called (in onStop), the answers arrive on the main thread */
    Registration listen(ChatRef chat, String myId, MessagesListener listener);

    interface SendFailure {
        void onRejected(Exception error);
    }

    /* writes on the phone at once and sends when there is network, so offline it shows as pending;
     * onRejected (main thread) runs only if the server refuses it, a slow network is not a failure */
    void send(ChatRef chat, String myId, String myName, String myInitials, String kind, String text,
              SendFailure onRejected);
}
