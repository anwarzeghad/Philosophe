package com.philosophers.philosopher_service.protocol;

import com.philosophers.philosopher_service.dto.FinishedMessage;
import com.philosophers.philosopher_service.dto.RequestMessage;
import com.philosophers.philosopher_service.dto.TokenMessage;

/** Envoi de messages aux autres philosophes (HTTP en production, simulé dans les tests). */
public interface PeerSender {

    void sendRequest(int peerId, RequestMessage message);

    void sendFinished(int peerId, FinishedMessage message);

    /** @return true si le jeton a bien été remis au destinataire */
    boolean sendToken(int peerId, TokenMessage message);
}
