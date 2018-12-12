package it.niedermann.nextcloud.deck.model.interfaces;

import java.util.Date;

import it.niedermann.nextcloud.deck.model.Account;

public interface RemoteEntity {
    Long getLocalId();
    Long getId();
    Account getAccount();
    void setLocalId(Long id);
    void setId(Long id);
    void setAccount (Account account);
    Date getLastModified();
    Date getLastModifiedLocal();
    void setLastModified(Date lastModified);
    void setLastModifiedLocal(Date lastModified);

}